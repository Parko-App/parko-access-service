package com.parko.access.service.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TicketPdfServiceTest {

    private final TicketPdfService service = new TicketPdfService();

    @Test
    void generateVisitorTicket_withPlate_containsExpectedText() throws IOException {
        byte[] pdf = service.generateVisitorTicket("TCK-ABC12345", "AB123CD", LocalDateTime.of(2026, 9, 19, 16, 30));

        assertThat(pdf).isNotEmpty();
        String text = extractText(pdf);
        assertThat(text).contains("VISITANTE");
        assertThat(text).contains("19/09/2026");
        assertThat(text).contains("16:30");
        assertThat(text).contains("Patente: AB123CD");
        assertThat(text).contains("TCK-ABC12345");
        assertThat(text).contains("DEBE ABONAR");
        assertThat(text).contains("QR");
    }

    @Test
    void generateVisitorTicket_withoutPlate_omitsPatenteLine() throws IOException {
        byte[] pdf = service.generateVisitorTicket("TCK-XYZ98765", null, LocalDateTime.of(2026, 9, 19, 16, 30));

        String text = extractText(pdf);
        assertThat(text).doesNotContain("Patente:");
        assertThat(text).contains("TCK-XYZ98765");
    }

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
