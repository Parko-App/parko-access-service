package com.parko.access.service.pdf;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketPdfServiceTest {

    private final TicketPdfService service = new TicketPdfService();

    @Test
    void generateVisitorTicket_withPlate_containsExpectedText() throws IOException {
        byte[] pdf = service.generateVisitorTicket("TCK-ABC12345", "AB123CD",
                LocalDateTime.of(2026, 9, 19, 16, 30), "http://localhost:8081/api/v1/access/sessions/abc/pay");

        assertThat(pdf).isNotEmpty();
        String text = extractText(pdf);
        assertThat(text).contains("VISITANTE");
        assertThat(text).contains("19/09/2026");
        assertThat(text).contains("16:30");
        assertThat(text).contains("Patente: AB123CD");
        assertThat(text).contains("TCK-ABC12345");
        assertThat(text).contains("DEBE ABONAR");
    }

    @Test
    void generateVisitorTicket_withoutPlate_omitsPatenteLine() throws IOException {
        byte[] pdf = service.generateVisitorTicket("TCK-XYZ98765", null,
                LocalDateTime.of(2026, 9, 19, 16, 30), "http://localhost:8081/api/v1/access/sessions/abc/pay");

        String text = extractText(pdf);
        assertThat(text).doesNotContain("Patente:");
        assertThat(text).contains("TCK-XYZ98765");
    }

    @Test
    void generateVisitorTicket_qrEncodesThePaymentUrl() throws Exception {
        String paymentUrl = "https://api.parko.site/api/v1/access/sessions/" + UUID.randomUUID() + "/pay";
        byte[] pdf = service.generateVisitorTicket("TCK-ABC12345", "AB123CD", LocalDateTime.now(), paymentUrl);

        assertThat(decodeQr(pdf)).isEqualTo(paymentUrl);
    }

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String decodeQr(byte[] pdf) throws IOException, NotFoundException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage rendered = new PDFRenderer(document).renderImageWithDPI(0, 300);
            LuminanceSource source = new BufferedImageLuminanceSource(rendered);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        }
    }
}
