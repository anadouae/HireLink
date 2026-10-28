package ma.ensat.hirelink.model;

/**
 * Étapes du pipeline de candidature (ATS bilatéral).
 * Parcours : SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW (fin positive). REJECTED et WITHDRAWN sont terminaux.
 */
public enum ApplicationStatus {
    SUBMITTED,
    SCREENING,
    SHORTLISTED,
    INTERVIEW,
    REJECTED,
    WITHDRAWN
}
