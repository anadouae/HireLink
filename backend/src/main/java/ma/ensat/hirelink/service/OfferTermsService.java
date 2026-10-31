package ma.ensat.hirelink.service;

import ma.ensat.hirelink.dto.ApiDtos.JobOfferRequest;
import ma.ensat.hirelink.model.CompensationType;
import ma.ensat.hirelink.model.ContractType;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.PayPeriod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Règles métier des conditions d'une offre (rémunération, durée, dates, avantages) selon le type de contrat.
 * Les champs qui ne concernent pas le type choisi sont ignorés, jamais enregistrés.
 */
@Service
public class OfferTermsService {

    private static final int MAX_BENEFIT_LENGTH = 60;
    private static final int MAX_INTERNSHIP_MONTHS = 12;

    /** Valide la requête et recopie les conditions sur l'offre. */
    public void apply(JobOffer offer, JobOfferRequest req) {
        ContractType type = ContractType.fromLabel(req.contractType())
                .orElseThrow(() -> bad("Type de contrat inconnu."));
        offer.setContractType(type.label());
        applyCompensation(offer, req, type);
        applyDuration(offer, req, type);
        applyDates(offer, req);
        if (type == ContractType.ALTERNANCE || type == ContractType.TEMPS_PARTIEL) {
            if (req.schedule() == null || req.schedule().isBlank()) {
                throw bad(type == ContractType.ALTERNANCE ? "Indiquez le rythme d'alternance." : "Indiquez le volume horaire.");
            }
            offer.setSchedule(req.schedule().trim());
        } else {
            offer.setSchedule(blankToNull(req.schedule()));
        }
        offer.setAgreementRequired(type.isInternship() ? req.agreementRequired() : null);
        offer.setHiringPossible(type.isInternship() ? req.hiringPossible() : null);
        offer.setProbationMonths(type == ContractType.CDI || type == ContractType.CDD ? req.probationMonths() : null);
        offer.setWorkMode(req.workMode());
        offer.setExperienceLevel(req.experienceLevel());
        offer.setEducationLevel(req.educationLevel());
        offer.setPositions(req.positions());
        offer.setBenefits(cleanBenefits(req.benefits()));
    }

    private void applyCompensation(JobOffer offer, JobOfferRequest req, ContractType type) {
        CompensationType comp = req.compensationType() == null ? CompensationType.UNSPECIFIED : req.compensationType();
        if (comp == CompensationType.UNPAID && !type.isInternship()) {
            throw bad("« Non rémunéré » n'est possible que pour un stage.");
        }
        offer.setCompensationType(comp);
        if (comp != CompensationType.PAID) {
            offer.setSalaryMin(null);
            offer.setSalaryMax(null);
            offer.setSalaryPeriod(null);
            offer.setSalaryBasis(null);
            offer.setCurrency(null);
            return;
        }
        if (req.salaryMin() == null || req.salaryMin() <= 0) throw bad("Indiquez le montant de la rémunération.");
        if (req.salaryMax() != null && req.salaryMax() < req.salaryMin()) {
            throw bad("Le montant maximum doit être supérieur ou égal au minimum.");
        }
        PayPeriod period = req.salaryPeriod() == null ? PayPeriod.MONTH : req.salaryPeriod();
        if (type.isInternship() && period != PayPeriod.MONTH) throw bad("La gratification d'un stage s'exprime par mois.");
        offer.setSalaryMin(req.salaryMin());
        offer.setSalaryMax(req.salaryMax() == null || req.salaryMax().equals(req.salaryMin()) ? null : req.salaryMax());
        offer.setSalaryPeriod(period);
        offer.setSalaryBasis(req.salaryBasis());
        offer.setCurrency(req.currency() == null ? "MAD" : req.currency());
    }

    private void applyDuration(JobOffer offer, JobOfferRequest req, ContractType type) {
        if (type.isDurationRequired() && req.durationMonths() == null) {
            throw bad("Indiquez la durée en mois.");
        }
        if (type.isInternship() && req.durationMonths() != null && req.durationMonths() > MAX_INTERNSHIP_MONTHS) {
            throw bad("Un stage dure au plus " + MAX_INTERNSHIP_MONTHS + " mois.");
        }
        offer.setDurationMonths(type == ContractType.CDI || type == ContractType.TEMPS_PARTIEL ? null : req.durationMonths());
    }

    private void applyDates(JobOffer offer, JobOfferRequest req) {
        boolean asap = Boolean.TRUE.equals(req.startAsap());
        LocalDate start = asap ? null : req.startDate();
        if (req.deadline() != null && !req.deadline().equals(offer.getDeadline()) && req.deadline().isBefore(LocalDate.now())) {
            throw bad("La date limite de candidature est déjà passée.");
        }
        if (start != null && req.deadline() != null && req.deadline().isAfter(start)) {
            throw bad("La date limite doit précéder la date de début.");
        }
        offer.setStartAsap(asap);
        offer.setStartDate(start);
        offer.setDeadline(req.deadline());
    }

    private static String cleanBenefits(String csv) {
        if (csv == null) return null;
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : csv.split("[,;]")) {
            String b = raw.replaceAll("[\\p{Cntrl}<>]", "").trim();
            if (!b.isEmpty()) unique.add(b.length() > MAX_BENEFIT_LENGTH ? b.substring(0, MAX_BENEFIT_LENGTH) : b);
        }
        return unique.isEmpty() ? null : String.join(", ", unique);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
