package ma.ensat.hirelink.service.web;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture tolérante des dates et montants renvoyés par les sources Web publiques.
 * Toute valeur illisible donne {@code null} (« non précisé ») : jamais d'exception.
 */
public final class WebJobFields {

    private WebJobFields() {}

    /** Date de publication : epoch (secondes ou millisecondes), ISO 8601, « aaaa-mm-jj hh:mm:ss » ou RFC 1123. */
    public static Instant date(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isNumber()) return epoch(node.asLong());
        return date(node.asText(null));
    }

    public static Instant date(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            if (s.matches("\\d{9,13}")) return epoch(Long.parseLong(s));
            if (s.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC);
            String iso = s.length() > 10 && s.charAt(10) == ' ' ? s.substring(0, 10) + "T" + s.substring(11) : s;
            try {
                return OffsetDateTime.parse(iso).toInstant();
            } catch (RuntimeException ignored) {
                // pas de fuseau : on tente la date locale (UTC)
            }
            try {
                return Instant.parse(iso);
            } catch (RuntimeException ignored) {
                // suite
            }
            try {
                return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
            } catch (RuntimeException ignored) {
                // suite
            }
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Instant epoch(long v) {
        if (v <= 0) return null;
        Instant at = v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
        // Date future ou absurde : non fiable.
        if (at.isAfter(Instant.now().plusSeconds(86_400)) || at.isBefore(Instant.parse("2000-01-01T00:00:00Z"))) {
            return null;
        }
        return at;
    }

    /** Montant entier positif (nombre ou texte numérique), sinon null. */
    public static Integer amount(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            double d = node.isNumber() ? node.asDouble() : Double.parseDouble(node.asText("").replaceAll("[^0-9.]", ""));
            if (d <= 0 || d > 100_000_000) return null;
            return (int) Math.round(d);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Premier champ non vide parmi {@code fields}. */
    public static JsonNode first(JsonNode n, String... fields) {
        if (n == null) return null;
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && !v.isNull() && !(v.isTextual() && v.asText().isBlank())) return v;
        }
        return null;
    }

    /** Rémunération extraite d'un texte libre (ex. champ {@code salary} Remotive). */
    public record ParsedPay(Integer min, Integer max, String currency, String period) {
        public static ParsedPay none() {
            return new ParsedPay(null, null, null, null);
        }
    }

    /**
     * Période de rémunération annoncée par la source : {@code YEAR}, {@code MONTH},
     * {@code WEEK}, {@code DAY}, {@code HOUR}, ou {@code null} si le texte ne la dit pas.
     * On ne déduit jamais une période d'après le seul montant.
     */
    public static String period(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return period(node.asText(null));
    }

    public static String period(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase(Locale.ROOT)
                .replace('é', 'e')
                .replace('è', 'e')
                .replace('/', ' ')
                .replace('-', ' ')
                .replace('_', ' ')
                .replace('.', ' ');
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        if (hasWord(s, "hourly", "hour", "hr", "heure", "heures")) return "HOUR";
        if (hasWord(s, "daily", "day", "jour", "jours")) return "DAY";
        if (hasWord(s, "weekly", "week", "wk", "semaine", "semaines")) return "WEEK";
        if (hasWord(s, "monthly", "month", "mo", "mois")) return "MONTH";
        if (hasWord(s, "yearly", "annual", "annually", "year", "yr", "annum", "annee", "annees")
                || s.contains("par an")
                || hasWord(s, "p a")) {
            return "YEAR";
        }
        return null;
    }

    private static boolean hasWord(String haystack, String... words) {
        for (String w : words) {
            if (haystack.equals(w) || haystack.matches(".*\\b" + w + "\\b.*")) return true;
        }
        return false;
    }

    /**
     * Lit un salaire en texte libre (« $170k - $200k », « $14/hour », « 3.5k–5k/month »).
     * La période n'est renseignée que si le texte la nomme (year, month, hour…).
     */
    public static ParsedPay pay(String raw) {
        if (raw == null) return ParsedPay.none();
        String s = raw.trim();
        if (s.isEmpty()) return ParsedPay.none();
        String period = period(s);
        String currency = currencyOf(s);
        List<Integer> amounts = new ArrayList<>();
        Matcher m = MONEY.matcher(s);
        while (m.find()) {
            Integer v = moneyToken(m.group(1), m.group(2));
            if (v != null) amounts.add(v);
        }
        if (amounts.isEmpty()) return ParsedPay.none();
        Integer min = amounts.get(0);
        Integer max = amounts.size() > 1 ? amounts.get(1) : null;
        if (max != null && min != null && max < min) {
            Integer tmp = min;
            min = max;
            max = tmp;
        }
        return new ParsedPay(min, max, currency, period);
    }

    private static final Pattern MONEY = Pattern.compile(
            "(?<!\\d)(\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*([kK])?\\b");

    private static Integer moneyToken(String number, String kilo) {
        try {
            boolean k = kilo != null && !kilo.isEmpty();
            String n = number;
            double d;
            if (k) {
                d = Double.parseDouble(n.replace(',', '.')) * 1000.0;
            } else if (n.matches("\\d{1,3}[.,]\\d{3}")) {
                d = Double.parseDouble(n.replace(".", "").replace(",", ""));
            } else {
                d = Double.parseDouble(n.replace(",", "."));
            }
            if (d <= 0 || d > 100_000_000) return null;
            return (int) Math.round(d);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Devise mentionnée dans un texte (symbole ou code), sinon null. */
    public static String currencyOf(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.indexOf('$') >= 0 || raw.matches("(?i).*\\busd\\b.*")) return "USD";
        if (raw.indexOf('€') >= 0 || raw.matches("(?i).*\\beur\\b.*")) return "EUR";
        if (raw.indexOf('£') >= 0 || raw.matches("(?i).*\\bgbp\\b.*")) return "GBP";
        if (raw.matches("(?i).*\\bchf\\b.*")) return "CHF";
        if (raw.matches("(?i).*\\bcad\\b.*")) return "CAD";
        if (raw.matches("(?i).*\\baud\\b.*")) return "AUD";
        if (raw.matches("(?i).*\\bmad\\b.*")) return "MAD";
        return null;
    }
}
