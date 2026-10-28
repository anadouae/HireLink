package ma.ensat.hirelink.controller;

import ma.ensat.hirelink.model.BillingInvoice;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.OrgRole;
import ma.ensat.hirelink.model.PlanTier;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.UserRepository;
import ma.ensat.hirelink.service.BillingCatalogService;
import ma.ensat.hirelink.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abonnement Premium : le plan est porté par le serveur (candidat → compte, recruteur → organisation).
 * Les crédits IA et les badges en découlent ; l'ancien indicateur côté navigateur n'existe plus.
 */
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('CANDIDAT','RECRUTEUR')")
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final BillingCatalogService billingCatalogService;

    public BillingController(UserRepository userRepository,
                             OrganizationService organizationService,
                             BillingCatalogService billingCatalogService) {
        this.userRepository = userRepository;
        this.organizationService = organizationService;
        this.billingCatalogService = billingCatalogService;
    }

    @PostMapping("/subscribe")
    @Transactional
    public Map<String, Object> subscribe(@AuthenticationPrincipal User user, @RequestBody(required = false) Map<String, Object> body) {
        String cycle = body != null && "year".equals(String.valueOf(body.get("cycle"))) ? "year" : "month";
        return switchTo(user, user.getRole() == Role.CANDIDAT ? PlanTier.PERFORMANCE : PlanTier.PREMIUM, cycle);
    }

    @DeleteMapping("/subscription")
    @Transactional
    public Map<String, Object> cancel(@AuthenticationPrincipal User user) {
        return switchTo(user, user.getRole() == Role.CANDIDAT ? PlanTier.ESSENTIEL : PlanTier.ACCES, "month");
    }

    private Map<String, Object> switchTo(User user, PlanTier tier, String cycle) {
        BillingInvoice invoice = null;
        if (user.getRole() == Role.CANDIDAT) {
            user.setPlanTier(tier);
            userRepository.save(user);
        } else if (user.getRole() == Role.RECRUTEUR) {
            Membership m = organizationService.requireMembership(user);
            if (m.getOrgRole() != OrgRole.OWNER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le propriétaire de l'organisation gère la formule");
            }
            if (m.getOrganization().getPlanTier() != tier) {
                invoice = organizationService.changePlan(m.getOrganization(), tier, cycle);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non disponible pour ce rôle");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planTier", tier.name());
        out.put("premium", tier == PlanTier.PREMIUM || tier == PlanTier.PERFORMANCE);
        out.put("invoice", invoice == null ? null : billingCatalogService.toInvoice(invoice));
        return out;
    }
}
