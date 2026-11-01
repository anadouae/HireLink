package ma.ensat.hirelink.model;

/**
 * Étapes du pipeline de candidature (ATS bilatéral).
 * Parcours HireLink : SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW (fin positive sur la plateforme).
 * OFFER / HIRED restent dans l'enum pour compatibilité données historiques, sans transition nouvelle.
 * Refus possible jusqu'à l'entretien ; REJECTED et WITHDRAWN sont terminaux.
 */
public enum ApplicationStatus {
    SUBMITTED,
    SCREENING,
    SHORTLISTED,
    INTERVIEW,
    /** @deprecated hors parcours HireLink — conservé pour données existantes */
    OFFER,
    /** @deprecated hors parcours HireLink — conservé pour données existantes */
    HIRED,
    REJECTED,
    WITHDRAWN
}
