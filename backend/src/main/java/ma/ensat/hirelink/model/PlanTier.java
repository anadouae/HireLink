package ma.ensat.hirelink.model;

/**
 * Catalogue Vague 2.
 * Entreprises : ESSENTIEL / PROFESSIONNEL / ENTREPRISE
 * Candidats : GRATUIT / PERFORMANCE
 * Alias legacy conservés pour lecture DB : ACCES → ESSENTIEL, PREMIUM → PROFESSIONNEL.
 * L’ancien ESSENTIEL candidat est migré vers GRATUIT (voir PlanCatalogMigrationService).
 */
public enum PlanTier {
    ESSENTIEL,
    PROFESSIONNEL,
    ENTREPRISE,
    GRATUIT,
    PERFORMANCE,
    /** @deprecated alias → ESSENTIEL (recruteur) */
    ACCES,
    /** @deprecated alias → PROFESSIONNEL */
    PREMIUM;

    /** Normalise les alias et l’ancien ESSENTIEL candidat. */
    public static PlanTier normalize(PlanTier tier, Role role) {
        if (tier == null) {
            return role == Role.CANDIDAT ? GRATUIT : ESSENTIEL;
        }
        if (tier == ACCES) return ESSENTIEL;
        if (tier == PREMIUM) return PROFESSIONNEL;
        // Avant migration : ESSENTIEL était le gratuit candidat.
        if (tier == ESSENTIEL && role == Role.CANDIDAT) return GRATUIT;
        return tier;
    }

    public PlanTier canonicalFor(Role role) {
        return normalize(this, role);
    }

    public boolean isRecruiterPlan() {
        PlanTier t = this == ACCES ? ESSENTIEL : (this == PREMIUM ? PROFESSIONNEL : this);
        return t == ESSENTIEL || t == PROFESSIONNEL || t == ENTREPRISE;
    }

    public boolean isCandidatePlan() {
        PlanTier t = this == ESSENTIEL ? GRATUIT : this; // legacy ambiguous
        return t == GRATUIT || t == PERFORMANCE;
    }

    public boolean isPaid() {
        PlanTier t = this == PREMIUM ? PROFESSIONNEL : this;
        return t == PROFESSIONNEL || t == ENTREPRISE || t == PERFORMANCE;
    }

    /**
     * Sièges d'équipe inclus. Source unique invitation / capacité.
     * Une org déjà au-dessus du plafond conserve ses membres (grandfather) :
     * seules les nouvelles invitations sont refusées.
     */
    public int seatLimit() {
        return switch (this == ACCES ? ESSENTIEL : (this == PREMIUM ? PROFESSIONNEL : this)) {
            case ESSENTIEL -> 1;
            case PROFESSIONNEL -> 5;
            case ENTREPRISE -> 25;
            default -> 1;
        };
    }

    /**
     * Offres actives (publiées, non suspendues) autorisées.
     * ENTREPRISE = illimité sous garde-fou technique.
     */
    public int activeOfferLimit() {
        return switch (this == ACCES ? ESSENTIEL : (this == PREMIUM ? PROFESSIONNEL : this)) {
            case ESSENTIEL -> 3;
            case PROFESSIONNEL -> 20;
            case ENTREPRISE -> 500; // garde-fou anti-abus (« illimité »)
            default -> 0;
        };
    }

    public boolean unlimitedOffers() {
        PlanTier t = this == PREMIUM ? PROFESSIONNEL : (this == ACCES ? ESSENTIEL : this);
        return t == ENTREPRISE;
    }

    /** Crédits IA par cycle (catalogue peut surcharger via commercial_plans). */
    public int defaultAiCredits() {
        return switch (this == ACCES ? ESSENTIEL : (this == PREMIUM ? PROFESSIONNEL : this)) {
            case ESSENTIEL -> 0;
            case PROFESSIONNEL -> 100;
            case ENTREPRISE -> 200;
            case GRATUIT -> 5;
            case PERFORMANCE -> 40;
            default -> 0;
        };
    }

    /** Prix mensuel TTC catalogue (MAD). */
    public int defaultMonthlyMadTtc() {
        return switch (this == ACCES ? ESSENTIEL : (this == PREMIUM ? PROFESSIONNEL : this)) {
            case ESSENTIEL, GRATUIT -> 0;
            case PROFESSIONNEL -> 299;
            case ENTREPRISE -> 699;
            case PERFORMANCE -> 39;
            default -> 0;
        };
    }

    public String displayLabel() {
        return switch (this == ACCES ? ESSENTIEL : (this == PREMIUM ? PROFESSIONNEL : this)) {
            case ESSENTIEL -> "Essentiel";
            case PROFESSIONNEL -> "Professionnel";
            case ENTREPRISE -> "Entreprise";
            case GRATUIT -> "Gratuit";
            case PERFORMANCE -> "Performance";
            default -> name();
        };
    }

    /** Cycle crédits : GRATUIT = 30 jours glissants ; autres = mois calendaire UTC. */
    public boolean rollingThirtyDayCredits() {
        return this == GRATUIT;
    }
}
