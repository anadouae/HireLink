package ma.ensat.hirelink.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CAPTCHA auto-adminé : aucune dépendance externe, aucune clé à enregistrer, fonctionne hors ligne.
 * Le serveur choisit entre une image SVG de caractères brouillés et une question de calcul, pour que
 * l'épreuve reste franchis sans lecture d'image.
 */
@Service
public class CaptchaService {

    /** Ni I, ni O, ni 0, ni 1 : un caractère ambigu coûte plus d'humains recalés que de robots stoppés. */
    private static final char[] GLYPHS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int GLYPH_COUNT = 5;
    private static final long TTL_MILLIS = 5 * 60_000L;
    private static final int MAX_PENDING = 5000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(byte[] answer, long expiresAt) {}

    public record Challenge(String token, String kind, String question, String svg, int expiresInSeconds) {}

    public Challenge issue() {
        prune();
        String token = UUID.randomUUID().toString().replace("-", "");
        Challenge challenge;
        String answer;
        if (random.nextInt(100) < 25) {
            int a = 11 + random.nextInt(78);
            int b = 3 + random.nextInt(9);
            boolean add = random.nextBoolean();
            answer = Integer.toString(add ? a + b : a - b);
            challenge = new Challenge(token, "question", "Combien font " + a + (add ? " + " : " − ") + b + " ?", null,
                    (int) (TTL_MILLIS / 1000));
        } else {
            StringBuilder word = new StringBuilder();
            for (int i = 0; i < GLYPH_COUNT; i++) word.append(GLYPHS[random.nextInt(GLYPHS.length)]);
            answer = word.toString();
            challenge = new Challenge(token, "image", "Recopiez les caractères de l'image", svg(answer),
                    (int) (TTL_MILLIS / 1000));
        }
        pending.put(token, new Pending(normalize(answer).getBytes(StandardCharsets.UTF_8), System.currentTimeMillis() + TTL_MILLIS));
        return challenge;
    }

    /** Jeton à usage unique : une réponse refusée le consomme aussi, il faut alors une nouvelle épreuve. */
    public void assertValid(String token, String answer) {
        if (token == null || token.isBlank() || answer == null || answer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Répondez au contrôle anti-robot avant de continuer.");
        }
        Pending p = pending.remove(token);
        if (p == null || p.expiresAt() < System.currentTimeMillis()
                || !MessageDigest.isEqual(p.answer(), normalize(answer).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Contrôle anti-robot incorrect ou expiré. Nouvelle question à résoudre.");
        }
    }

    private String svg(String word) {
        int width = 34 + word.length() * 26;
        StringBuilder s = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"58\" viewBox=\"0 0 ").append(width).append(" 58\" role=\"img\" ")
                .append("aria-label=\"Caractères à recopier\" focusable=\"false\">")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#f6f1ea\"/>");
        for (int i = 0; i < 4; i++) {
            s.append("<path d=\"M0 ").append(6 + random.nextInt(46)).append(" Q ").append(width / 2).append(' ')
                    .append(6 + random.nextInt(46)).append(' ').append(width).append(' ').append(6 + random.nextInt(46))
                    .append("\" stroke=\"#d9cbb8\" stroke-width=\"1.6\" fill=\"none\"/>");
        }
        // Dessin dans un ordre différent de l'ordre de lecture : lire les <text> dans l'ordre du DOM ne donne pas la réponse.
        List<Integer> drawOrder = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) drawOrder.add(i);
        Collections.shuffle(drawOrder, random);
        for (int idx : drawOrder) {
            int cx = 26 + idx * 26;
            s.append("<text x=\"").append(cx).append("\" y=\"").append(38 + random.nextInt(7))
                    .append("\" font-family=\"ui-monospace,Consolas,monospace\" font-size=\"29\" font-weight=\"700\" ")
                    .append("fill=\"#7a2032\" transform=\"rotate(").append(-18 + random.nextInt(37)).append(' ')
                    .append(cx).append(" 30)\">").append(word.charAt(idx)).append("</text>");
        }
        for (int i = 0; i < 14; i++) {
            s.append("<circle cx=\"").append(random.nextInt(width)).append("\" cy=\"").append(4 + random.nextInt(50))
                    .append("\" r=\"1.3\" fill=\"#b8a58c\"/>");
        }
        return s.append("</svg>").toString();
    }

    private static String normalize(String value) {
        return value.replaceAll("[^0-9A-Za-zÀ-ÿ]", "").toUpperCase(Locale.ROOT);
    }

    private void prune() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        if (pending.size() > MAX_PENDING) pending.clear();
    }
}
