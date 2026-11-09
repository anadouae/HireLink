package ma.ensat.hirelink.service;

import ma.ensat.hirelink.model.BillingInvoice;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Facture A4. Les accents français passent par une police Unicode lorsqu'elle est disponible. */
@Service
public class InvoicePdfService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    private static final String[] FONT_CANDIDATES = {
            "C:\\Windows\\Fonts\\arial.ttf",
            "C:\\Windows\\Fonts\\segoeui.ttf",
            "C:\\Windows\\Fonts\\tahoma.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    };

    @Value("${hirelink.billing.issuer-name:HireLink}")
    private String issuerName;

    @Value("${hirelink.billing.issuer-address:}")
    private String issuerAddress;

    @Value("${hirelink.billing.issuer-ice:}")
    private String issuerIce;

    public byte[] render(BillingInvoice invoice) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDFont font = loadFont(doc);
            PDFont bold = font;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 800;
                y = line(cs, bold, 16, 48, y, safe(issuerName));
                y = line(cs, font, 10, 48, y - 4, legalLine());
                y -= 18;
                y = line(cs, bold, 18, 48, y, "Facture " + invoice.getNumber());
                y = line(cs, font, 11, 48, y - 6, "Émise le " + WHEN.format(invoice.getIssuedAt())
                        + "  ·  " + statusLabel(invoice));
                y -= 16;
                y = line(cs, bold, 12, 48, y, "Destinataire");
                y = line(cs, font, 11, 48, y - 2, safe(invoice.getBillToName()));
                if (invoice.getBillToEmail() != null && !invoice.getBillToEmail().isBlank()) {
                    y = line(cs, font, 11, 48, y, safe(invoice.getBillToEmail()));
                }
                if (invoice.getBillToExtra() != null && !invoice.getBillToExtra().isBlank()) {
                    y = line(cs, font, 11, 48, y, safe(invoice.getBillToExtra()));
                }
                y -= 18;
                y = line(cs, font, 11, 48, y, "Référence  " + invoice.getNumber());
                y = line(cs, font, 11, 48, y, "Opération  Abonnement " + safe(invoice.getPlanLabel())
                        + " · " + ("year".equals(invoice.getCycle()) ? "annuel" : "mensuel"));
                y -= 16;
                String vat = vatLine(invoice);
                y = line(cs, font, 11, 48, y, "Montant HT     " + money(invoice.getAmountHt()));
                y = line(cs, font, 11, 48, y, vat);
                y = line(cs, bold, 12, 48, y - 4, "Total TTC      " + money(invoice.getAmountTtc()) + " MAD");
                y -= 16;
                if (invoice.getTransactionRef() != null && !invoice.getTransactionRef().isBlank()) {
                    y = line(cs, font, 11, 48, y, "Transaction  " + invoice.getTransactionRef());
                } else if ("PAYEE".equals(invoice.canonicalStatus()) && invoice.getAmountTtc() == 0) {
                    y = line(cs, font, 11, 48, y, "Aucun règlement : montant nul.");
                }
                y -= 28;
                line(cs, font, 9, 48, y, "Document de démonstration. Aucun débit bancaire réel n'est associé à cette facture.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF impossible", e);
        }
    }

    private String legalLine() {
        boolean address = issuerAddress != null && !issuerAddress.isBlank();
        boolean ice = issuerIce != null && !issuerIce.isBlank();
        if (!address && !ice) {
            return "Adresse et ICE de l'émetteur : non renseignés.";
        }
        return (address ? issuerAddress : "Adresse non renseignée") + (ice ? "  ·  ICE " + issuerIce : "  ·  ICE non renseigné");
    }

    private static String vatLine(BillingInvoice invoice) {
        if (Boolean.FALSE.equals(invoice.getVatConfigured())) {
            return "TVA            non configurée";
        }
        return "TVA enregistrée " + money(invoice.getTva()) + " MAD";
    }

    private static String statusLabel(BillingInvoice invoice) {
        return switch (invoice.canonicalStatus()) {
            case "PAYEE" -> "Payée";
            case "NON_PAYEE" -> "Non payée";
            case "ANNULEE" -> "Annulée";
            case "AVOIR" -> "Avoir historique";
            default -> invoice.getStatus();
        };
    }

    private static String money(double n) {
        return String.format(java.util.Locale.FRANCE, "%,.2f", n);
    }

    private static String safe(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        return raw.replace('\n', ' ').replace('\r', ' ');
    }

    private PDFont loadFont(PDDocument doc) throws Exception {
        InputStream classpath = InvoicePdfService.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf");
        if (classpath != null) {
            try (classpath) {
                return PDType0Font.load(doc, classpath, true);
            }
        }
        for (String candidate : FONT_CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return PDType0Font.load(doc, path.toFile());
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private float line(PDPageContentStream cs, PDFont font, float size, float x, float y, String text) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(fit(font, text));
        cs.endText();
        return y - (size + 6);
    }

    /** Helvetica standard ne couvre pas tous les accents : on remplace seulement dans ce cas. */
    private static String fit(PDFont font, String text) {
        if (font instanceof PDType0Font) return text;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c < 32 || c > 255 ? '?' : c);
        }
        return out.toString();
    }
}
