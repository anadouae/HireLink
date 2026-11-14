package ma.ensat.hirelink.service.external;

import java.util.regex.Pattern;

/** Conversion HTML -> texte brut pour descriptions ATS. N'invente aucun contenu. */
public final class ExternalHtmlText {
    private static final Pattern ENTITY = Pattern.compile("&#x([0-9a-fA-F]+);|&#([0-9]+);");

    private ExternalHtmlText() {}

    public static String toPlain(String html, int max) {
        if (html == null || html.isBlank()) return null;
        String text = html;
        // Greenhouse content can be double-entity-encoded; decode twice safely.
        text = decodeEntities(removeMarkup(text));
        text = decodeEntities(text);
        text = text.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        text = removeMarkup(text).replaceAll("\\s+", " ").trim();
        if (text.isBlank()) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String removeMarkup(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ");
    }

    private static String decodeEntities(String text) {
        return ENTITY.matcher(text).replaceAll(m -> {
            try {
                int code = m.group(1) != null ? Integer.parseInt(m.group(1), 16) : Integer.parseInt(m.group(2));
                if (code <= 0 || code > 0x10FFFF) return " ";
                if (Character.isISOControl(code) && code != '\n' && code != '\t') return " ";
                return new String(Character.toChars(code));
            } catch (RuntimeException e) {
                return " ";
            }
        });
    }
}