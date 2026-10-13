package ma.ensat.hirelink.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Scan anti-fraude contenu d'offres (fake jobs) — Niveau 2.
 * Décision humaine pour les cas ambigus ; blocage dur sur patterns d'arnaque.
 */
@Service
public class FraudOfferScanService {

    private static final List<String> HARD_BLOCK = List.of(
            "whatsapp obligatoire",
            "contactez whatsapp",
            "écrire sur whatsapp",
            "formation payante",
            "payer pour postuler",
            "frais d'inscription",
            "argent à l'avance",
            "argent a l'avance",
            "virement avant",
            "envoyez de l'argent",
            "crypto",
            "bitcoin",
            "5000€/semaine",
            "5000 euros/semaine",
            "sans expérience 10000",
            "travail à domicile payé 5000",
            "aucun diplôme requis salaire"
    );

    private static final List<String> SOFT_FLAG = List.of(
            "whatsapp",
            "telegram",
            "western union",
            "moneygram",
            "carte cadeau",
            "gift card",
            "urgent cash",
            "commission uniquement",
            "aucun entretien"
    );

    public record ScanResult(
            boolean blockPublish,
            boolean softFlag,
            List<String> hits,
            String reasonCode,
            String message
    ) {}

    public ScanResult scan(String title, String description) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'');
        List<String> hard = new ArrayList<>();
        for (String m : HARD_BLOCK) {
            if (text.contains(m)) hard.add(m);
        }
        if (!hard.isEmpty()) {
            return new ScanResult(true, false, hard, "SCAM_KEYWORDS",
                    "Publication refusée — contenu à risque (fake job). Reformulez sans demandes d’argent / WhatsApp obligatoire.");
        }
        List<String> soft = new ArrayList<>();
        for (String m : SOFT_FLAG) {
            if (text.contains(m)) soft.add(m);
        }
        if (!soft.isEmpty()) {
            return new ScanResult(false, true, soft, "SUSPICIOUS_KEYWORDS",
                    "Offre mise en brouillon (quarantaine douce) — termes sensibles détectés. Un admin peut réexaminer.");
        }
        return new ScanResult(false, false, List.of(), "OK", "Contenu acceptable");
    }
}
