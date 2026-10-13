package ma.ensat.hirelink.model;

/**
 * Étapes du pipeline de candidature (ATS bilatéral).
 * Parcours géré : SUBMITTED → … → INTERVIEW (fin positive).
 * OFFER / ACCEPTED restent dans l'enum pour compatibilité, mais hors transitions actives.
 */
public enum ApplicationStatus {
    SUBMITTED,
    SCREENING,
    SHORTLISTED,
    INTERVIEW,
    /** @deprecated hors périmètre — non atteignable via advance() */
    OFFER,
    /** @deprecated hors périmètre — non atteignable via advance() */
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}
