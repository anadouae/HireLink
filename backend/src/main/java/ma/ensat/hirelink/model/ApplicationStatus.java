package ma.ensat.hirelink.model;

/**
 * Étapes du pipeline de candidature (ATS bilatéral).
 * Parcours : SUBMITTED → SCREENING → SHORTLISTED → INTERVIEW → OFFER → HIRED (issue positive).
 * Le refus reste possible jusqu'à l'offre ; REJECTED, WITHDRAWN et HIRED sont terminaux.
 */
public enum ApplicationStatus {
    SUBMITTED,
    SCREENING,
    SHORTLISTED,
    INTERVIEW,
    OFFER,
    HIRED,
    REJECTED,
    WITHDRAWN
}
