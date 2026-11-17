package ma.ensat.hirelink.service;

import ma.ensat.hirelink.service.external.AshbyJobParser;
import ma.ensat.hirelink.service.external.GreenhouseJobParser;
import ma.ensat.hirelink.service.external.JoobleJobParser;
import ma.ensat.hirelink.service.external.LeverJobParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registre des sources d'offres externes autorisees (libelles, URL d'accueil, activation). */
@Component
public class ExternalSourceCatalog {

    public static final String REMOTE_OK = "REMOTE_OK";
    public static final String REMOTIVE = "REMOTIVE";
    public static final String ARBEITNOW = "ARBEITNOW";
    public static final String HIMALAYAS = "HIMALAYAS";
    public static final String JOBICY = "JOBICY";
    public static final String ADZUNA = "ADZUNA";
    public static final String WEB_FALLBACK = "WEB_FALLBACK";
    public static final String MAROC_EMPLOI = "MAROC_EMPLOI";
    public static final String GREENHOUSE = GreenhouseJobParser.SOURCE;
    public static final String LEVER = LeverJobParser.SOURCE;
    public static final String ASHBY = AshbyJobParser.SOURCE;
    public static final String JOOBLE = JoobleJobParser.SOURCE;

    public record SourceInfo(String code, String label, String homeUrl, boolean enabled, String kind) {}

    private final boolean remoteOkEnabled;
    private final boolean marocEnabled;
    private final boolean greenhouseEnabled;
    private final boolean leverEnabled;
    private final boolean ashbyEnabled;
    private final boolean joobleEnabled;

    public ExternalSourceCatalog(
            @Value("${hirelink.external-jobs.remoteok.enabled:false}") boolean remoteOkEnabled,
            @Value("${hirelink.external-jobs.maroc.enabled:true}") boolean marocEnabled,
            @Value("${hirelink.external-jobs.greenhouse.enabled:false}") boolean greenhouseEnabled,
            @Value("${hirelink.external-jobs.lever.enabled:false}") boolean leverEnabled,
            @Value("${hirelink.external-jobs.ashby.enabled:false}") boolean ashbyEnabled,
            @Value("${hirelink.external-jobs.jooble.enabled:false}") boolean joobleEnabled) {
        this.remoteOkEnabled = remoteOkEnabled;
        this.marocEnabled = marocEnabled;
        this.greenhouseEnabled = greenhouseEnabled;
        this.leverEnabled = leverEnabled;
        this.ashbyEnabled = ashbyEnabled;
        this.joobleEnabled = joobleEnabled;
    }

    public SourceInfo require(String code) {
        SourceInfo info = find(code);
        if (info == null) throw new IllegalArgumentException("Source inconnue : " + code);
        return info;
    }

    public SourceInfo find(String code) {
        if (code == null) return null;
        String key = code.trim().toUpperCase();
        return all().stream().filter(s -> s.code().equals(key)).findFirst().orElse(null);
    }

    public String labelOf(String code) {
        SourceInfo s = find(code);
        return s != null ? s.label() : code;
    }

    public String homeOf(String code) {
        SourceInfo s = find(code);
        return s != null ? s.homeUrl() : "";
    }

    public List<SourceInfo> all() {
        // Remotive / Arbeitnow : connus pour libellé + attribution (recherche Web éphémère),
        // enabled=false pour ne pas les entraîner dans le sync external-jobs.
        // Remote OK : label/home toujours disponibles ; enabled suit uniquement le flag de sync.
        return List.of(
                new SourceInfo(MAROC_EMPLOI, "Emploi Maroc (partenaires)", "https://hirelink.ma", marocEnabled, "maroc"),
                new SourceInfo(REMOTIVE, "Remotive", "https://remotive.com", false, "web-search"),
                new SourceInfo(ARBEITNOW, "Arbeitnow", "https://www.arbeitnow.com", false, "web-search"),
                new SourceInfo(HIMALAYAS, "Himalayas", "https://himalayas.app", false, "web-search"),
                new SourceInfo(JOBICY, "Jobicy", "https://jobicy.com", false, "web-search"),
                new SourceInfo(ADZUNA, "Adzuna", "https://www.adzuna.com", false, "web-search"),
                new SourceInfo("WE_WORK_REMOTELY", "We Work Remotely", "https://weworkremotely.com", false, "web-search"),
                new SourceInfo(WEB_FALLBACK, "Web (Tavily/Serper)", "", false, "web-fallback"),
                new SourceInfo(REMOTE_OK, "Remote OK", "https://remoteok.com", remoteOkEnabled, "remote"),
                new SourceInfo(GREENHOUSE, "Greenhouse", "https://www.greenhouse.io", greenhouseEnabled, "ats"),
                new SourceInfo(LEVER, "Lever", "https://www.lever.co", leverEnabled, "ats"),
                new SourceInfo(ASHBY, "Ashby", "https://www.ashbyhq.com", ashbyEnabled, "ats"),
                new SourceInfo(JOOBLE, "Jooble Maroc", "https://ma.jooble.org", joobleEnabled, "aggregator")
        );
    }

    public List<SourceInfo> enabled() {
        return all().stream().filter(SourceInfo::enabled).toList();
    }

    public Map<String, Object> toMap(SourceInfo s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", s.code());
        row.put("label", s.label());
        row.put("homeUrl", s.homeUrl());
        row.put("enabled", s.enabled());
        row.put("kind", s.kind());
        return row;
    }
}