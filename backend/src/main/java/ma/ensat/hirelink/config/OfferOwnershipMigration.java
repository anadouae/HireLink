package ma.ensat.hirelink.config;

import ma.ensat.hirelink.repository.JobOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emprunte d'une offre figée à la création (A1) : la colonne organization_id est ajoutée par
 * ddl-auto=update et reste NULL pour les lignes déjà en base. Cette passe la remplit une fois pour
 * toutes, à partir de l'appartenance active de l'auteur, sans rien écraser et sans attendre que les
 * données de démonstration soient activées. Elle ne supprime ni ne purge aucune ligne.
 */
@Component
public class OfferOwnershipMigration {

    private static final Logger log = LoggerFactory.getLogger(OfferOwnershipMigration.class);

    private final JobOfferRepository jobOfferRepository;

    public OfferOwnershipMigration(JobOfferRepository jobOfferRepository) {
        this.jobOfferRepository = jobOfferRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void attachLegacyOffersToTheirOrganization() {
        try {
            int attached = jobOfferRepository.backfillMissingOrganization();
            if (attached > 0) {
                log.info("Rattachement des offres à leur organisation : {} ligne(s) mise(s) à jour (A1).", attached);
            }
            long pending = jobOfferRepository.countWithoutOrganization();
            if (pending > 0) {
                log.warn("{} offre(s) restent sans organisation : leur auteur n'a aucune appartenance "
                        + "active unique (R02). À trancher par un administrateur.", pending);
            }
        } catch (Exception e) {
            log.warn("Rattachement des offres legacy ignoré ({})", e.getMessage());
        }
    }
}
