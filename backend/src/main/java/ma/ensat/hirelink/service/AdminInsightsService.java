package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.model.CommercialPlan;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.SentEmail;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.ApplicationRepository;
import ma.ensat.hirelink.repository.BillingInvoiceRepository;
import ma.ensat.hirelink.repository.JobOfferRepository;
import ma.ensat.hirelink.repository.OfferReportRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import ma.ensat.hirelink.repository.SentEmailRepository;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chiffres du tableau de bord d'administration, calculés à la demande sur les tables réelles.
 * Revenu mensuel = somme des prix mensuels des formules payantes en cours (entreprises actives
 * et candidats Performance) ; encaissé = factures réglées.
 */
@Service
public class AdminInsightsService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
    private static final List<PlanTier> COMPANY_PLANS = List.of(PlanTier.ESSENTIEL, PlanTier.PROFESSIONNEL, PlanTier.ENTREPRISE);
    private static final List<PlanTier> CANDIDATE_PLANS = List.of(PlanTier.GRATUIT, PlanTier.PERFORMANCE);

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final BillingInvoiceRepository invoices;
    private final BillingCatalogService catalog;
    private final JobOfferRepository offers;
    private final ApplicationRepository applications;
    private final OfferReportRepository reports;
    private final SentEmailRepository emails;
    private final UserSessionRepository sessions;

    public AdminInsightsService(UserRepository users, OrganizationRepository organizations, BillingInvoiceRepository invoices,
                                BillingCatalogService catalog, JobOfferRepository offers, ApplicationRepository applications,
                                OfferReportRepository reports, SentEmailRepository emails, UserSessionRepository sessions) {
        this.users = users;
        this.organizations = organizations;
        this.invoices = invoices;
        this.catalog = catalog;
        this.offers = offers;
        this.applications = applications;
        this.reports = reports;
        this.emails = emails;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(String granularity) {
        Instant now = Instant.now();
        List<User> allUsers = users.findAll();
        List<Organization> allOrgs = organizations.findAllWithOwner();

        // ---- formules en cours
        Map<PlanTier, Integer> companyCount = new LinkedHashMap<>();
        COMPANY_PLANS.forEach(t -> companyCount.put(t, 0));
        int companiesActive = 0, companiesDisabled = 0, companiesRemoved = 0, companiesVerified = 0, companiesToVerify = 0;
        for (Organization o : allOrgs) {
            if (o.isRemoved()) { companiesRemoved++; continue; }
            if (!o.isAccessActive()) { companiesDisabled++; continue; }
            companiesActive++;
            if (o.isVerified()) companiesVerified++; else companiesToVerify++;
            PlanTier t = PlanTier.normalize(o.getPlanTier(), Role.RECRUTEUR);
            if (t.isPaid() && expired(o.getSubscriptionEndsAt(), now)) t = PlanTier.ESSENTIEL;
            companyCount.merge(t, 1, Integer::sum);
        }
        Map<PlanTier, Integer> candidateCount = new LinkedHashMap<>();
        CANDIDATE_PLANS.forEach(t -> candidateCount.put(t, 0));
        int active = 0, inactive = 0, candidates = 0, recruiters = 0, admins = 0;
        for (User u : allUsers) {
            if (u.isActive()) active++; else inactive++;
            Role r = u.getRole();
            if (r == Role.CANDIDAT) {
                candidates++;
                if (!u.isActive()) continue;
                PlanTier t = PlanTier.normalize(u.getPlanTier(), Role.CANDIDAT);
                if (t == PlanTier.PERFORMANCE && expired(u.getSubscriptionEndsAt(), now)) t = PlanTier.GRATUIT;
                candidateCount.merge(t == PlanTier.PERFORMANCE ? PlanTier.PERFORMANCE : PlanTier.GRATUIT, 1, Integer::sum);
            } else if (r == Role.RECRUTEUR) recruiters++;
            else if (r == Role.ADMINISTRATEUR) admins++;
        }

        List<Map<String, Object>> revenueByPlan = new ArrayList<>();
        int monthly = 0;
        for (PlanTier t : List.of(PlanTier.ESSENTIEL, PlanTier.PROFESSIONNEL, PlanTier.ENTREPRISE, PlanTier.PERFORMANCE)) {
            CommercialPlan plan = catalog.require(t);
            int count = t == PlanTier.PERFORMANCE ? candidateCount.get(t) : companyCount.get(t);
            int unit = plan.getMonthlyMad();
            monthly += unit * count;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", t.name());
            row.put("label", plan.getLabel());
            row.put("audience", t == PlanTier.PERFORMANCE ? "Candidats" : "Entreprises");
            row.put("subscribers", count);
            row.put("monthlyMad", unit);
            row.put("monthlyTotal", unit * count);
            revenueByPlan.add(row);
        }

        // ---- encaissé (factures réglées)
        double collected = 0, collected30 = 0, unpaidAmount = 0;
        int unpaid = 0;
        Map<String, Double> collectedByPlan = new LinkedHashMap<>();
        Instant d30 = now.minus(30, ChronoUnit.DAYS);
        for (BillingInvoice inv : invoices.findAll()) {
            String st = inv.canonicalStatus();
            if ("PAYEE".equals(st) && inv.getAmountTtc() > 0) {
                collected += inv.getAmountTtc();
                Instant at = inv.getPaidAt() != null ? inv.getPaidAt() : inv.getIssuedAt();
                if (at != null && at.isAfter(d30)) collected30 += inv.getAmountTtc();
                String label = planLabel(inv.getPlanCode(), inv.getPlanLabel());
                collectedByPlan.merge(label, inv.getAmountTtc(), Double::sum);
            } else if (inv.payable()) {
                unpaid++;
                unpaidAmount += inv.getAmountTtc();
            }
        }

        Map<String, Object> revenue = new LinkedHashMap<>();
        revenue.put("monthlyActive", monthly);
        revenue.put("byPlan", revenueByPlan);
        revenue.put("collectedTotal", round(collected));
        revenue.put("collectedLast30Days", round(collected30));
        List<Map<String, Object>> cbp = new ArrayList<>();
        collectedByPlan.forEach((k, v) -> cbp.add(Map.of("label", k, "amount", round(v))));
        revenue.put("collectedByPlan", cbp);

        Map<String, Object> distribution = new LinkedHashMap<>();
        distribution.put("companies", tierRows(companyCount));
        distribution.put("candidates", tierRows(candidateCount));

        Map<String, Object> people = new LinkedHashMap<>();
        people.put("total", allUsers.size());
        people.put("active", active);
        people.put("inactive", inactive);
        people.put("candidates", candidates);
        people.put("recruiters", recruiters);
        people.put("admins", admins);
        people.put("seenLast30Days", sessions.countDistinctUsersActiveSince(d30));

        Map<String, Object> companies = new LinkedHashMap<>();
        companies.put("total", allOrgs.size());
        companies.put("active", companiesActive);
        companies.put("disabled", companiesDisabled);
        companies.put("removed", companiesRemoved);
        companies.put("verified", companiesVerified);
        companies.put("toVerify", companiesToVerify);

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("offers", offers.count());
        activity.put("publishedOffers", offers.countByPublishedTrue());
        activity.put("applications", applications.count());

        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("reports", reports.countByStatus("PENDING"));
        pending.put("suspendedOffers", offers.countBySuspendedTrue());
        pending.put("unpaidInvoices", unpaid);
        pending.put("unpaidAmount", round(unpaidAmount));
        pending.put("companiesWithoutBadge", companiesToVerify);
        pending.put("failedEmails7Days", emails.countByStatusAndCreatedAtAfter(SentEmail.Status.FAILED, now.minus(7, ChronoUnit.DAYS)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("revenue", revenue);
        out.put("distribution", distribution);
        out.put("people", people);
        out.put("companies", companies);
        out.put("activity", activity);
        out.put("pending", pending);
        out.put("signups", signups(allUsers, "month".equalsIgnoreCase(granularity)));
        out.put("generatedAt", now.toString());
        return out;
    }

    /** Inscriptions par semaine (12 dernières) ou par mois (12 derniers), candidats et recruteurs séparés. */
    private Map<String, Object> signups(List<User> all, boolean monthly) {
        LocalDate today = LocalDate.now(ZONE);
        List<LocalDate> starts = new ArrayList<>();
        if (monthly) {
            YearMonth ym = YearMonth.from(today);
            for (int i = 11; i >= 0; i--) starts.add(ym.minusMonths(i).atDay(1));
        } else {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            for (int i = 11; i >= 0; i--) starts.add(monday.minusWeeks(i));
        }
        int n = starts.size();
        int[] cand = new int[n];
        int[] rec = new int[n];
        for (User u : all) {
            if (u.getCreatedAt() == null || u.getRole() == Role.ADMINISTRATEUR) continue;
            LocalDate d = u.getCreatedAt().atZone(ZONE).toLocalDate();
            if (d.isBefore(starts.get(0))) continue;
            int idx = n - 1;
            for (int i = 0; i < n - 1; i++) {
                if (!d.isBefore(starts.get(i)) && d.isBefore(starts.get(i + 1))) { idx = i; break; }
            }
            if (u.getRole() == Role.CANDIDAT) cand[idx]++; else rec[idx]++;
        }
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("start", starts.get(i).toString());
            b.put("candidates", cand[i]);
            b.put("recruiters", rec[i]);
            buckets.add(b);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("granularity", monthly ? "month" : "week");
        out.put("buckets", buckets);
        return out;
    }

    private List<Map<String, Object>> tierRows(Map<PlanTier, Integer> counts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((t, c) -> rows.add(Map.of("code", t.name(), "label", catalog.require(t).getLabel(), "count", c)));
        return rows;
    }

    private String planLabel(String code, String fallback) {
        try {
            PlanTier t = PlanTier.valueOf(code == null ? "" : code.trim().toUpperCase());
            PlanTier canon = t == PlanTier.ACCES ? PlanTier.ESSENTIEL : t == PlanTier.PREMIUM ? PlanTier.PROFESSIONNEL : t;
            return canon.displayLabel();
        } catch (Exception e) {
            return fallback == null || fallback.isBlank() ? "Autre" : fallback;
        }
    }

    private static boolean expired(Instant endsAt, Instant now) {
        return endsAt != null && endsAt.isBefore(now);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
