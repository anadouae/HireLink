package ma.ensat.hirelink.model;

/**
 * Étapes du pipeline de candidature (ATS bilatéral).
 */
public enum ApplicationStatus {
    SUBMITTED,
    SCREENING,
    SHORTLISTED,
    INTERVIEW,
    OFFER,
    ACCEPTED,
    REJECTED,
    WITHDRAWN
}
