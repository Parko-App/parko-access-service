package com.parko.access.service.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class TicketPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float PAGE_WIDTH = 226f;
    private static final float MARGIN = 15f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    public byte[] generateVisitorTicket(String ticketNumber, String visitorPlate, LocalDateTime entryAt) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, 340f));
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = page.getMediaBox().getHeight() - 30;

                y = writeCentered(content, FONT_BOLD, 14, "VISITANTE", y);
                y -= 20;
                y = writeLine(content, FONT_REGULAR, 10, "Fecha: " + entryAt.format(DATE_FORMAT), y);
                y = writeLine(content, FONT_REGULAR, 10, "Hora: " + entryAt.format(TIME_FORMAT), y);
                if (visitorPlate != null && !visitorPlate.isBlank()) {
                    y = writeLine(content, FONT_REGULAR, 10, "Patente: " + visitorPlate, y);
                }
                y = writeLine(content, FONT_REGULAR, 10, "Ticket: " + ticketNumber, y);
                y -= 15;
                y = writeWrapped(content, FONT_BOLD, 9,
                        "DEBE ABONAR ESTE TICKET ANTES DE SALIR DEL ESTACIONAMIENTO", y);
                y -= 15;

                drawPlaceholderQr(content, y);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del ticket", e);
        }
    }

    private float writeCentered(PDPageContentStream content, PDFont font, float fontSize, String text, float y) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float x = MARGIN + (CONTENT_WIDTH - textWidth) / 2;
        return writeAt(content, font, fontSize, text, x, y);
    }

    private float writeLine(PDPageContentStream content, PDFont font, float fontSize, String text, float y) throws IOException {
        return writeAt(content, font, fontSize, text, MARGIN, y);
    }

    private float writeAt(PDPageContentStream content, PDFont font, float fontSize, String text, float x, float y) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - (fontSize + 4);
    }

    private float writeWrapped(PDPageContentStream content, PDFont font, float fontSize, String text, float y) throws IOException {
        for (String line : wrap(text, font, fontSize, CONTENT_WIDTH)) {
            y = writeCentered(content, font, fontSize, line, y);
        }
        return y;
    }

    private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000 * fontSize > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void drawPlaceholderQr(PDPageContentStream content, float y) throws IOException {
        float qrSize = 80f;
        float qrX = MARGIN + (CONTENT_WIDTH - qrSize) / 2;
        float qrY = y - qrSize;
        content.addRect(qrX, qrY, qrSize, qrSize);
        content.stroke();
        writeAt(content, FONT_REGULAR, 8, "QR", qrX + qrSize / 2 - 6, qrY + qrSize / 2 - 4);
    }
}
