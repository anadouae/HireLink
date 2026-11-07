package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.CompanyFollow;
import ma.ensat.hirelink.model.JobOffer;
import ma.ensat.hirelink.model.Membership;
import ma.ensat.hirelink.model.Organization;
import ma.ensat.hirelink.model.Role;
import ma.ensat.hirelink.model.User;
import ma.ensat.hirelink.repository.CompanyFollowRepository;
import ma.ensat.hirelink.repository.MembershipRepository;
import ma.ensat.hirelink.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Suivi d'entreprise côté candidat. L'entreprise ne voit qu'un total : ni nom, ni profil, ni e-mail.
 * Un candidat sans profil public ne peut donc rien perdre de confidentiel en suivant une société.
 */
@Service
public class CompanyFollowService {

    private final CompanyFollowRepository followRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final NotificationService notificationService;

    public CompanyFollowService(
            CompanyFollowRepository followRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            NotificationService notificationService
    ) {
        this.followRepository = followRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(User candidate) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CompanyFollow follow : followRepository.findByCandidateUserIdOrderByCreatedAtDesc(candidate.getId())) {
            Organization org = organizationRepository.findById(follow.getOrganizationId()).orElse(null);
            if (org == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("organizationId", org.getId());
            row.put("name", org.getName());
            row.put("logoUrl", org.getLogoUrl() == null ? "" : org.getLogoUrl());
            row.put("since", follow.getCreatedAt().toString());
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> follow(User candidate, Long organizationId) {
        requireCandidate(candidate);
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable"));
        if (find(candidate, organizationId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Entreprise déjà suivie");
        }
        CompanyFollow follow = new CompanyFollow();
        follow.setCandidateUserId(candidate.getId());
        follow.setOrganizationId(org.getId());
        followRepository.save(follow);
        return body("FOLLOWED", org.getId());
    }

    @Transactional
    public Map<String, Object> unfollow(User candidate, Long organizationId) {
        requireCandidate(candidate);
        CompanyFollow follow = find(candidate, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cette entreprise n'est pas dans vos suivis"));
        followRepository.delete(follow);
        return body("UNFOLLOWED", organizationId);
    }

    @Transactional(readOnly = true)
    public long count(Long organizationId) {
        return followRepository.countByOrganizationId(organizationId);
    }

    /**
     * Nouvelle offre publiée : chaque abonné reçoit la notification. Appelé dans la transaction de
     * publication, donc {@link NotificationService#push} émet le flux temps réel seulement après commit.
     */
    public void announceNewOffer(JobOffer offer) {
        Long organizationId = organizationIdOf(offer.getRecruiter());
        if (organizationId == null) return;
        List<Long> followers = followRepository.findCandidateIdsByOrganization(organizationId);
        if (followers.isEmpty()) return;
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        String company = org != null && org.getName() != null ? org.getName() : "Une entreprise suivie";
        StringBuilder detail = new StringBuilder("« ").append(offer.getTitle()).append(" »");
        if (offer.getContractType() != null && !offer.getContractType().isBlank()) {
            detail.append(" · ").append(offer.getContractType());
        }
        if (offer.getLocation() != null && !offer.getLocation().isBlank()) {
            detail.append(" · ").append(offer.getLocation());
        }
        detail.append(" — publiée à l'instant.");
        for (Long candidateId : followers) {
            notificationService.push("offer:" + offer.getId(), candidateId,
                    "Nouvelle offre chez " + company, detail.toString(), "FOLLOW");
        }
    }

    /** Organisation active du recruteur ; une offre sans équipe ne peut pas être suivie. */
    @Transactional(readOnly = true)
    public Long organizationIdOf(User recruiter) {
        return membershipRepository.findByUserIdAndActiveTrue(recruiter.getId()).stream()
                .map(Membership::getOrganization)
                .filter(Objects::nonNull)
                .map(Organization::getId)
                .findFirst()
                .orElse(null);
    }

    private java.util.Optional<CompanyFollow> find(User candidate, Long organizationId) {
        return followRepository.findByCandidateUserIdAndOrganizationId(candidate.getId(), organizationId);
    }

    private Map<String, Object> body(String status, Long organizationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("organizationId", organizationId);
        return out;
    }

    private static void requireCandidate(User user) {
        if (user == null || user.getRole() != Role.CANDIDAT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux candidats");
        }
    }
}
