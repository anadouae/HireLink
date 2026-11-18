package ma.ensat.hirelink.service.web;

import ma.ensat.hirelink.service.WebJobDiscovery.DiscoveredJob;
import ma.ensat.hirelink.service.external.ExternalHtmlText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * We Work Remotely — flux public RSS (categories/*.rss).
 * Le JSON {@code /remote-jobs.json} renvoie HTTP 403 depuis cet environnement (2026-11) :
 * on n'invente pas d'accès JSON et on n'effectue pas de scraping HTML.
 * Attribution : We Work Remotely + deep-link d'annonce.
 */
@Component
public class WeWorkRemotelyWebJobSource implements WebJobSource {

    public static final String CODE = "WE_WORK_REMOTELY";

    private static final Pattern ITEM = Pattern.compile("<item>([\\s\\S]*?)</item>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile("<link>([\\s\\S]*?)</link>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC = Pattern.compile("<description>([\\s\\S]*?)</description>", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGION = Pattern.compile("<region>([\\s\\S]*?)</region>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CATEGORY = Pattern.compile("<category>([\\s\\S]*?)</category>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUB_DATE = Pattern.compile("<pubDate>([\\s\\S]*?)</pubDate>", Pattern.CASE_INSENSITIVE);

    private final JobGeoVerifier geo;
    private final WebJobFetchCache cache;
    private final boolean enabled;
    private final String feedUrl;
    private final long cacheTtlSec;

    public WeWorkRemotelyWebJobSource(
            JobGeoVerifier geo,
            WebJobFetchCache cache,
            @Value("${hirelink.web-search.wwr.enabled:true}") boolean enabled,
            @Value("${hirelink.web-search.wwr.feed:https://weworkremotely.com/remote-jobs.rss}") String feedUrl,
            @Value("${hirelink.web-search.wwr.cache-ttl-sec:1800}") long cacheTtlSec) {
        this.geo = geo;
        this.cache = cache;
        this.enabled = enabled;
        this.feedUrl = feedUrl;
        this.cacheTtlSec = Math.max(300, cacheTtlSec);
    }

    @Override public String code() { return CODE; }
    @Override public String label() { return "We Work Remotely"; }
    @Override public String homeUrl() { return "https://weworkremotely.com"; }
    @Override public String coverageNote() {
        return "We Work Remotely : flux RSS public (JSON /remote-jobs.json inaccessible ici — HTTP 403). Remote international.";
    }
    @Override public Set<String> countryHints() { return Set.of(); }
    @Override public boolean isReady() { return enabled; }
    @Override public String notReadyReason() {
        return enabled ? "" : "We Work Remotely désactivé (hirelink.web-search.wwr.enabled=false).";
    }

    @Override
    public List<DiscoveredJob> fetch(String query, String countryCode, String location, int limit) {
        if (!enabled) return List.of();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String cacheKey = "WWR|" + q + "|" + (countryCode == null ? "ALL" : countryCode)
                + "|" + (location == null ? "" : location);
        List<DiscoveredJob> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.size() <= limit ? cached : cached.subList(0, limit);
        }
        try {
            String xml = WebJobHttp.getText(feedUrl, "application/rss+xml, application/xml, text/xml, */*");
            List<DiscoveredJob> out = new ArrayList<>();
            Matcher items = ITEM.matcher(xml);
            while (items.find()) {
                String block = items.group(1);
                String rawTitle = unescape(tag(TITLE, block));
                String deep = WebJobHttp.deepJobUrl(unescape(tag(LINK, block)));
                if (rawTitle == null || rawTitle.isBlank() || deep == null) continue;
                CompanyTitle ct = splitCompanyTitle(rawTitle);
                String region = unescape(tag(REGION, block));
                String category = unescape(tag(CATEGORY, block));
                String description = ExternalHtmlText.toPlain(unescape(tag(DESC, block)), 4000);
                if (!q.isEmpty()) {
                    String blob = (rawTitle + " " + (region == null ? "" : region) + " "
                            + (category == null ? "" : category) + " "
                            + (description == null ? "" : description)).toLowerCase(Locale.ROOT);
                    if (!blob.contains(q) && !matchesTokens(blob, q)) continue;
                }
                String inferred = geo.inferCountryCode(region);
                DiscoveredJob job = new DiscoveredJob(
                        ct.title(),
                        ct.company(),
                        region,
                        region,
                        inferred,
                        category,
                        "REMOTE",
                        category,
                        description,
                        deep,
                        deep,
                        code(),
                        label(),
                        homeUrl()).withDetails(WebJobFields.date(unescape(tag(PUB_DATE, block))), null, null, null);
                if (!geo.matches(job, countryCode, location)) continue;
                out.add(job);
                if (out.size() >= Math.max(limit, 40)) break;
            }
            cache.put(cacheKey, out, cacheTtlSec);
            return out.size() <= limit ? out : out.subList(0, limit);
        } catch (Exception e) {
            throw new IllegalStateException("We Work Remotely indisponible : " + e.getMessage(), e);
        }
    }

    private static boolean matchesTokens(String blob, String q) {
        for (String t : q.split("\\s+")) {
            if (t.length() < 2) continue;
            if (!blob.contains(t)) return false;
        }
        return true;
    }

    private static String tag(Pattern p, String block) {
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String unescape(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("<![CDATA[") && s.endsWith("]]>")) {
            s = s.substring(9, s.length() - 3).trim();
        }
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
    }

    private record CompanyTitle(String company, String title) {}

    private static CompanyTitle splitCompanyTitle(String raw) {
        int idx = raw.indexOf(':');
        if (idx > 0 && idx < raw.length() - 1) {
            String company = raw.substring(0, idx).trim();
            String title = raw.substring(idx + 1).trim();
            if (!company.isEmpty() && !title.isEmpty()) return new CompanyTitle(company, title);
        }
        return new CompanyTitle("Entreprise (via We Work Remotely)", raw.trim());
    }
}
