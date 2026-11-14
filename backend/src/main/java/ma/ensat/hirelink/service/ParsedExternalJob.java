package ma.ensat.hirelink.service;

import java.time.Instant;

/** Annonce normalisee, independante de la source (Remote OK, partenaires Maroc, ATS...). */
public record ParsedExternalJob(
        String externalId,
        String title,
        String company,
        String originUrl,
        String applyUrl,
        String location,
        String contractLabel,
        String description,
        String skills,
        Integer salaryMin,
        Integer salaryMax,
        Instant publishedAt,
        String boardKey,
        String countryCode
) {
    /** Compatibilite parseurs legacy sans board/pays. */
    public ParsedExternalJob(
            String externalId,
            String title,
            String company,
            String originUrl,
            String applyUrl,
            String location,
            String contractLabel,
            String description,
            String skills,
            Integer salaryMin,
            Integer salaryMax,
            Instant publishedAt) {
        this(externalId, title, company, originUrl, applyUrl, location, contractLabel,
                description, skills, salaryMin, salaryMax, publishedAt, null, null);
    }
}