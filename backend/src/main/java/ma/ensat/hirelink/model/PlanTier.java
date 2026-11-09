package ma.ensat.hirelink.model;

/** Paliers CDC §9 — crédits F16. */
public enum PlanTier {
    ESSENTIEL,
    PERFORMANCE,
    ACCES,
    PREMIUM;

    /**
     * Sièges d'équipe inclus dans la formule (CDC §9). Source unique : invitation, acceptation d'une
     * invitation et affichage de la capacité doivent lire le même nombre, sinon le front promet plus
     * que le serveur n'autorise.
     */
    public int seatLimit() {
        return this == PREMIUM ? 25 : 5;
    }
}
