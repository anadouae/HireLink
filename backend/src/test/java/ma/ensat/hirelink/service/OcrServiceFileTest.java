package ma.ensat.hirelink.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcrServiceFileTest {

    private final OcrService ocr = new OcrService();

    @Test
    void unPdfTextuelContientLeStage() throws Exception {
        byte[] pdf = pdf("EXPERIENCES\nStage developpeur Java, Atlas Digital\n");
        OcrService.OcrResult result = ocr.extract(new MockMultipartFile("file", "cv.pdf", "application/pdf", pdf));
        assertTrue(result.textLayer());
        CvSectionParser.Parsed parsed = ocr.analyze(result.text(), result.suggestedSkills(), result.textLayer(), result.note());
        assertEquals("STAGE", parsed.experiences().get(0).kind());
        assertEquals("Atlas Digital", parsed.experiences().get(0).organization());
    }

    @Test
    void unPdfSansTexteNePretendPasAvoirFaitDocr() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            pdf = out.toByteArray();
        }
        OcrService.OcrResult result = ocr.extract(new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf));
        assertFalse(result.textLayer());
        CvSectionParser.Parsed parsed = ocr.analyze(result.text(), result.suggestedSkills(), result.textLayer(), result.note());
        assertTrue(parsed.experiences().isEmpty());
        assertTrue(result.note().contains("OCR"));
    }

    @Test
    void uneImageNestPasLueParOcr() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        OcrService.OcrResult result = ocr.extract(new MockMultipartFile("file", "cv.png", "image/png", png));
        assertFalse(result.textLayer());
        assertTrue(result.suggestedSkills().isEmpty());
        assertTrue(result.note().contains("OCR"));
    }

    @Test
    void unFichierInvalideEstRefuse() {
        byte[] junk = new byte[]{0, 1, 2, 3, 4, 5};
        assertThrows(IllegalArgumentException.class,
                () -> ocr.extract(new MockMultipartFile("file", "cv.bin", "application/octet-stream", junk)));
    }

    @Test
    void unFichierTropVolumineuxEstRefuse() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(13L * 1024 * 1024);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ocr.extract(file));
        assertTrue(error.getMessage().contains("volumineux"));
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                for (String line : text.split("\n")) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -18);
                }
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
