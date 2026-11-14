package ma.ensat.hirelink.service;

/**
 * Qualite d'une URL d'offre externe.
 * Une URL https syntaxiquement valide ne prouve pas que l'annonce existe encore :
 * DEEP = chemin d'annonce plausible ; UNKNOWN = ambigu ; GENERIC_HOME / INVALID = non fiables.
 */
public enum UrlQuality {
    /** Chemin d'annonce ou de formulaire specifique (id, slug, /jobs/123, /apply/...). */
    DEEP,
    /** https correct mais chemin trop generique pour garantir l'annonce exacte. */
    UNKNOWN,
    /** Racine de site ou index carrieres sans identifiant d'annonce. */
    GENERIC_HOME,
    /** Schema non https, malformee, ou host/path inutilisable. */
    INVALID;

    public boolean isQuarantineCandidate() {
        return this == GENERIC_HOME || this == INVALID;
    }

    /** Affichable dans la vitrine (avec CTA prudent si ce n'est pas DEEP). */
    public boolean isPubliclyListable() {
        return this == DEEP || this == UNKNOWN;
    }

    /** Le bouton peut etre presente comme acces direct a l'annonce. */
    public boolean isDirectAccessTrusted() {
        return this == DEEP;
    }
}