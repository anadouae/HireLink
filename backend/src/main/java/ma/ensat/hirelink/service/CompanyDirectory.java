package ma.ensat.hirelink.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Référentiel interne (ICE + domaine public). En production : API OMPIC / data.gov.ma.
 * Sert à l’auto-vérification et aux logos d’entreprise.
 */
public final class CompanyDirectory {

    public record Entry(
            String ice,
            String name,
            String domain,
            String sector,
            String city,
            String employees
    ) {
        public String logoUrl() {
            if (domain == null || domain.isBlank()) return "";
            return "https://www.google.com/s2/favicons?domain=" + domain + "&sz=128";
        }

        public String website() {
            if (domain == null || domain.isBlank()) return "";
            return "https://" + domain;
        }
    }

    private static final Map<String, Entry> BY_ICE = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_NAME = new LinkedHashMap<>();

    static {
        put("001544289000035", "Atlas Digital", "atlasdigital.ma", "Technologie", "Tétouan", "11-50");
        put("001234567000012", "Nova RH", "novarh.ma", "Services", "Casablanca", "11-50");
        put("002468135000098", "HireDesk Services", "hiredesk.ma", "Services", "Rabat", "11-50");
        put("002100000000011", "OCP Group", "ocpgroup.ma", "Industrie", "Casablanca", "1001-5000");
        put("002100000000022", "Maroc Telecom", "iam.ma", "Télécoms", "Rabat", "1001-5000");
        put("002100000000033", "Attijariwafa bank", "attijariwafabank.com", "Banque", "Casablanca", "1001-5000");
        put("002100000000044", "Royal Air Maroc", "royalairmaroc.com", "Transport", "Casablanca", "1001-5000");
        put("002100000000055", "Renault Group Maroc", "renault.ma", "Automobile", "Tanger", "1001-5000");
        put("002100000000066", "Stellantis", "stellantis.com", "Automobile", "Kénitra", "1001-5000");
        put("002100000000077", "inwi", "inwi.ma", "Télécoms", "Casablanca", "1001-5000");
        put("002100000000088", "Intelcia", "intelcia.com", "Services", "Rabat", "1001-5000");
        put("002100000000099", "Capgemini Maroc", "capgemini.com", "Technologie", "Casablanca", "1001-5000");
        put("002100000000101", "CGI Maroc", "cgi.com", "Technologie", "Casablanca", "1001-5000");
        put("002100000000112", "HPS", "hps-worldwide.com", "Technologie", "Casablanca", "251-1000");
        put("002100000000123", "Bank of Africa", "bankofafrica.ma", "Banque", "Casablanca", "1001-5000");
        put("002100000000134", "Marjane", "marjane.ma", "Commerce", "Casablanca", "1001-5000");
        put("002100000000145", "Decathlon Maroc", "decathlon.ma", "Commerce", "Casablanca", "251-1000");
        put("002100000000156", "Cosumar", "cosumar.co.ma", "Agroalimentaire", "Casablanca", "251-1000");
        put("002100000000167", "Managem", "managemgroup.com", "Industrie", "Marrakech", "251-1000");
        put("002100000000178", "ONCF", "oncf.ma", "Transport", "Rabat", "1001-5000");
        put("002100000000189", "Tanger Med", "tangermed.ma", "Logistique", "Tanger", "251-1000");
        put("002100000000190", "Accor", "accor.com", "Hôtellerie", "Marrakech", "1001-5000");
        put("002100000000201", "Centrale Danone", "centraledanone.ma", "Agroalimentaire", "Casablanca", "251-1000");
        put("002100000000212", "LabelVie", "labelvie.ma", "Commerce", "Casablanca", "1001-5000");
        put("002100000000223", "Wafa Assurance", "wafaassurance.ma", "Assurance", "Casablanca", "251-1000");
        put("002100000000234", "Alstom Maroc", "alstom.com", "Industrie", "Casablanca", "251-1000");
    }

    private CompanyDirectory() {}

    private static void put(String ice, String name, String domain, String sector, String city, String employees) {
        Entry e = new Entry(ice, name, domain, sector, city, employees);
        BY_ICE.put(ice, e);
        BY_NAME.put(fold(name), e);
    }

    public static Optional<Entry> byIce(String ice) {
        if (ice == null) return Optional.empty();
        return Optional.ofNullable(BY_ICE.get(ice.replaceAll("\\s+", "")));
    }

    public static Optional<Entry> byName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_NAME.get(fold(name)));
    }

    public static Collection<Entry> all() {
        return BY_ICE.values();
    }

    public static String logoFor(String ice, String name) {
        return byIce(ice).or(() -> byName(name)).map(Entry::logoUrl).orElse("");
    }

    public static String fold(String value) {
        String n = java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        return n;
    }
}
