package ma.ensat.hirelink.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gabarit unique des e-mails HireLink : version HTML aux couleurs de l'application (bordeaux, crème, champagne ;
 * polices Fraunces / DM Sans avec repli système) et version texte brut. Tout contenu dynamique est échappé.
 */
public final class EmailTemplates {

    public record Fact(String label, String value) {}

    /** Contenu d'un message : titre, paragraphes, tableau de faits, code mis en avant et bouton d'action optionnels. */
    public record Email(String heading, List<String> paragraphs, List<Fact> facts, String highlight, String ctaLabel, String ctaUrl) {

        public static Email of(String heading, String... paragraphs) {
            return new Email(heading, List.of(paragraphs), List.of(), null, null, null);
        }

        public Email withFacts(Fact... facts) {
            return new Email(heading, paragraphs, List.of(facts), highlight, ctaLabel, ctaUrl);
        }

        public Email withHighlight(String code) {
            return new Email(heading, paragraphs, facts, code, ctaLabel, ctaUrl);
        }

        public Email withButton(String label, String url) {
            return new Email(heading, paragraphs, facts, highlight, label, url);
        }
    }

    private static final String BORDEAUX = "#7a2032";
    private static final String CREAM = "#f5f1ec";
    private static final String CHAMPAGNE = "#c9a66b";
    private static final String INK = "#1a1214";
    private static final String MUTED = "#5e5558";
    private static final String HEADING_FONT = "Fraunces,Georgia,'Times New Roman',serif";
    private static final String BODY_FONT = "'DM Sans',Arial,Helvetica,sans-serif";
    private static final Pattern URL = Pattern.compile("https?://[^\\s<]+");

    private EmailTemplates() {}

    public static String html(Email e, String footer) {
        StringBuilder b = new StringBuilder(2048);
        b.append("<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(esc(e.heading())).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:").append(CREAM).append(";\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:").append(CREAM).append(";padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:600px;width:100%;\">")
                .append("<tr><td style=\"background:").append(BORDEAUX).append(";border-radius:14px 14px 0 0;padding:20px 28px;font-family:").append(HEADING_FONT)
                .append(";font-size:24px;color:#ffffff;letter-spacing:.2px;\">Hire<span style=\"color:").append(CHAMPAGNE).append(";\">Link</span></td></tr>")
                .append("<tr><td style=\"background:#ffffff;padding:30px 28px 26px;border:1px solid rgba(26,18,20,.08);border-top:0;font-family:").append(BODY_FONT)
                .append(";color:").append(INK).append(";font-size:15px;line-height:1.6;\">")
                .append("<h1 style=\"margin:0 0 16px;font-family:").append(HEADING_FONT).append(";font-size:22px;line-height:1.3;color:").append(BORDEAUX).append(";\">")
                .append(esc(e.heading())).append("</h1>");
        for (String p : e.paragraphs()) {
            b.append("<p style=\"margin:0 0 14px;\">").append(linkify(esc(p)).replace("\n", "<br>")).append("</p>");
        }
        if (e.highlight() != null) {
            b.append("<p style=\"margin:18px 0;text-align:center;font-family:").append(HEADING_FONT).append(";font-size:34px;letter-spacing:8px;color:")
                    .append(BORDEAUX).append(";background:").append(CREAM).append(";border:1px dashed ").append(CHAMPAGNE)
                    .append(";border-radius:12px;padding:14px 8px;\">").append(esc(e.highlight())).append("</p>");
        }
        if (!e.facts().isEmpty()) {
            b.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:6px 0 18px;border-left:3px solid ")
                    .append(CHAMPAGNE).append(";background:").append(CREAM).append(";\">");
            for (Fact f : e.facts()) {
                b.append("<tr><td style=\"padding:8px 14px;font-size:13px;color:").append(MUTED).append(";width:34%;vertical-align:top;\">").append(esc(f.label()))
                        .append("</td><td style=\"padding:8px 14px;font-size:14px;font-weight:600;color:").append(INK).append(";\">")
                        .append(linkify(esc(f.value())).replace("\n", "<br>")).append("</td></tr>");
            }
            b.append("</table>");
        }
        if (e.ctaUrl() != null) {
            b.append("<p style=\"margin:22px 0 6px;\"><a href=\"").append(esc(e.ctaUrl())).append("\" style=\"display:inline-block;background:").append(BORDEAUX)
                    .append(";color:#ffffff;text-decoration:none;font-weight:600;padding:12px 24px;border-radius:999px;\">").append(esc(e.ctaLabel())).append("</a></p>");
        }
        b.append("</td></tr><tr><td style=\"padding:16px 28px;font-family:").append(BODY_FONT).append(";font-size:12px;line-height:1.5;color:").append(MUTED)
                .append(";text-align:center;\">").append(linkify(esc(footer)).replace("\n", "<br>"))
                .append("<br>HireLink — plateforme de recrutement · ENSA Tétouan</td></tr></table></td></tr></table></body></html>");
        return b.toString();
    }

    public static String text(Email e, String footer) {
        StringBuilder b = new StringBuilder();
        b.append(e.heading()).append("\n\n");
        e.paragraphs().forEach(p -> b.append(p).append("\n\n"));
        if (e.highlight() != null) b.append("    ").append(e.highlight()).append("\n\n");
        e.facts().forEach(f -> b.append(f.label()).append(" : ").append(f.value()).append("\n"));
        if (!e.facts().isEmpty()) b.append("\n");
        if (e.ctaUrl() != null) b.append(e.ctaLabel()).append(" : ").append(e.ctaUrl()).append("\n\n");
        return b.append("--\n").append(footer).append("\nHireLink — plateforme de recrutement · ENSA Tétouan\n").toString();
    }

    /** Échappement HTML (contenu et attributs). */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Les adresses http(s) du texte (déjà échappé) deviennent des liens cliquables. */
    private static String linkify(String escaped) {
        Matcher m = URL.matcher(escaped);
        return m.replaceAll(r -> "<a href=\"" + r.group() + "\" style=\"color:" + BORDEAUX + ";word-break:break-all;\">" + r.group() + "</a>");
    }
}
