package ma.ensat.hirelink.model;

/** Rôles au sein d'une Organisation (CDC §6.1). */
public enum OrgRole {
    OWNER,
    RECRUTEUR,
    HIRING_MANAGER;

    /**
     * Nom montré aux utilisateurs. « Owner » est écarté : il fait croire que le compte incarne l'entreprise,
     * alors qu'il désigne seulement le créateur de l'espace. Les constantes de l'enum ne changent pas —
     * l'API, les jetons et la base gardent OWNER / RECRUTEUR / HIRING_MANAGER.
     */
    public String libelleFr() {
        return switch (this) {
            case OWNER -> "Créateur";
            case RECRUTEUR -> "Recruteur";
            case HIRING_MANAGER -> "Responsable du poste";
        };
    }
}
