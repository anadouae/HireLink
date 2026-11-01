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
        /** Logo officiel connu (fichier Wikimedia ou site de l'entreprise) ; vide sinon : l'interface affiche les initiales. */
        public String logoUrl() {
            return OFFICIAL_LOGOS.getOrDefault(name, "");
        }

        public String website() {
            if (domain == null || domain.isBlank()) return "";
            return "https://" + domain;
        }
    }

    /** Logos officiels vérifiés (HTTP 200, image) ; même liste que database/update_logos_entreprises_maroc.sql. */
    private static final Map<String, String> OFFICIAL_LOGOS = Map.ofEntries(
            Map.entry("OCP Group", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d9/Ocp-group.png/330px-Ocp-group.png"),
            Map.entry("Maroc Telecom", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/Maroc_Telecom_logo.png/330px-Maroc_Telecom_logo.png"),
            Map.entry("Attijariwafa bank", "https://upload.wikimedia.org/wikipedia/fr/thumb/1/1d/Logo_AWB.svg/330px-Logo_AWB.svg.png"),
            Map.entry("Royal Air Maroc", "https://upload.wikimedia.org/wikipedia/fr/thumb/c/c7/Logo_Royal_Air_Maroc_2013_%28Horizontal%29.svg/330px-Logo_Royal_Air_Maroc_2013_%28Horizontal%29.svg.png"),
            Map.entry("Renault Group Maroc", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/2021_Renault_Group_logo.svg/330px-2021_Renault_Group_logo.svg.png"),
            Map.entry("Stellantis", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Stellantis.svg/330px-Stellantis.svg.png"),
            Map.entry("inwi", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/Logo_inwi.svg/330px-Logo_inwi.svg.png"),
            Map.entry("Orange Maroc", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Orange_logo.svg/330px-Orange_logo.svg.png"),
            Map.entry("CIH Bank", "https://upload.wikimedia.org/wikipedia/fr/b/b2/Logo_CIH.png"),
            Map.entry("Deloitte", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ed/Logo_of_Deloitte.svg/330px-Logo_of_Deloitte.svg.png"),
            Map.entry("Yazaki", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Yazaki_company_logo.svg/330px-Yazaki_company_logo.svg.png"),
            Map.entry("Lear", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/Lear_Corporation_logo.svg/330px-Lear_Corporation_logo.svg.png"),
            Map.entry("Valeo", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2b/Valeo_Logo.svg/330px-Valeo_Logo.svg.png"),
            Map.entry("Intelcia", "https://www.intelcia.com/themes/custom/intelcia/corpo/images/logo-gradient.svg"),
            Map.entry("Capgemini Maroc", "https://upload.wikimedia.org/wikipedia/fr/thumb/b/b5/Capgemini_Logo.svg/330px-Capgemini_Logo.svg.png"),
            Map.entry("CGI Maroc", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/32/CGI_logo.svg/330px-CGI_logo.svg.png"),
            Map.entry("HPS", "https://upload.wikimedia.org/wikipedia/fr/thumb/6/6d/Logo_hps_0.png/330px-Logo_hps_0.png"),
            Map.entry("Bank of Africa", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Bank_of_Africa_Logo.png/330px-Bank_of_Africa_Logo.png"),
            Map.entry("Marjane", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/71/Marjane_Holding_Logo.svg/330px-Marjane_Holding_Logo.svg.png"),
            Map.entry("Decathlon Maroc", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bc/Decathlon_-_logo_%28France%2C_2024%29.svg/330px-Decathlon_-_logo_%28France%2C_2024%29.svg.png"),
            Map.entry("Cosumar", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5c/Logo_nouveau_cosumar.png/330px-Logo_nouveau_cosumar.png"),
            Map.entry("Managem", "https://upload.wikimedia.org/wikipedia/fr/thumb/1/11/Managem.png/330px-Managem.png"),
            Map.entry("ONCF", "https://upload.wikimedia.org/wikipedia/fr/thumb/e/e5/Logo_ONCF.svg/330px-Logo_ONCF.svg.png"),
            Map.entry("Tanger Med", "https://upload.wikimedia.org/wikipedia/commons/thumb/9/95/Tanger_Med_logo.png/330px-Tanger_Med_logo.png"),
            Map.entry("Accor", "https://upload.wikimedia.org/wikipedia/fr/thumb/c/c3/Accor_Logo.png/330px-Accor_Logo.png"),
            Map.entry("Centrale Danone", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c9/Logo-Centrale-Danone.png/330px-Logo-Centrale-Danone.png"),
            Map.entry("LabelVie", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e7/Iabel%27vie.png/330px-Iabel%27vie.png"),
            Map.entry("Wafa Assurance", "https://upload.wikimedia.org/wikipedia/fr/3/38/Waa.gif"),
            Map.entry("Alstom Maroc", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1c/Alstom_logo.svg/330px-Alstom_logo.svg.png"),
            Map.entry("Banque Populaire", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/Logo_BCP.svg/330px-Logo_BCP.svg.png"),
            Map.entry("Akwa Group", "https://upload.wikimedia.org/wikipedia/commons/8/83/Akwa_Group.png"),
            Map.entry("Afriquia", "https://upload.wikimedia.org/wikipedia/fr/thumb/3/3f/Afriquialogo.png/330px-Afriquialogo.png"),
            Map.entry("ONEE", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/25/ONEE_logo.png/330px-ONEE_logo.png")
    );

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
        String key = IceFormatValidator.normalize(ice);
        return Optional.ofNullable(BY_ICE.get(key));
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
