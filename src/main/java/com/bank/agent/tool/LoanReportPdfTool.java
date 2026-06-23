package com.bank.agent.tool;

import com.bank.agent.repository.ReportRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AI Tool: generates a PDF loan status report and exposes it for download.
 * Uses Apache PDFBox to build the document; stores in the system temp directory.
 */
@Component
public class LoanReportPdfTool {

    private static final Logger log = LoggerFactory.getLogger(LoanReportPdfTool.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);

    // Column X positions and widths for the 6-column table (A4 portrait, 40pt margins)
    private static final float[] COL_X     = {40, 95, 235, 305, 385, 465};
    private static final float[] COL_WIDTH  = {55, 140, 70,  80,  80,  70};
    private static final String[] COL_HEADERS = {"Loan ID", "Customer", "Type", "Principal (₹)", "Outstanding (₹)", "Status"};

    private static final float PAGE_WIDTH  = PDRectangle.A4.getWidth();   // 595
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();  // 842
    private static final float ROW_HEIGHT  = 20f;
    private static final float HEADER_Y    = 760f;

    private final ReportRepository reportRepository;

    public LoanReportPdfTool(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Called by the LLM to generate a PDF loan report ready for download.
     *
     * @param department Optional department filter. Pass empty string to include all departments.
     * @return A message containing the download URL for the generated PDF.
     */
    @Tool(description = """
            Generates a PDF loan status report from the internal database and makes it available for download.
            Optionally filter by department (Retail Banking, Corporate Banking, Rural Banking).
            Pass empty string for department to include all loans.
            Returns a download URL that the user can use to save the PDF report.
            """)
    public String generateLoanReportPdf(String department) {
        log.debug("LoanReportPdfTool invoked — department: {}", department);

        try {
            List<Map<String, Object>> loans = reportRepository.getLoanStatus(department);

            if (loans.isEmpty()) {
                return "{\"status\": \"NO_DATA\", \"message\": \"No loan records found for the given filter.\"}";
            }

            Path reportDir = ensureReportDirectory();
            String filename = "loan-report-" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
            Path outputPath = reportDir.resolve(filename);

            buildPdf(loans, department, outputPath);

            String downloadUrl = "/api/agent/download/" + filename;
            log.info("PDF report generated: {} ({} records)", outputPath, loans.size());

            return String.format(
                    "{\"status\": \"SUCCESS\", \"records\": %d, \"downloadUrl\": \"%s\", " +
                    "\"message\": \"Loan report PDF generated with %d records. Download at: %s\"}",
                    loans.size(), downloadUrl, loans.size(), downloadUrl);

        } catch (Exception e) {
            log.error("Failed to generate PDF report", e);
            return "{\"status\": \"ERROR\", \"message\": \"Failed to generate PDF: " + e.getMessage() + "\"}";
        }
    }

    // ── PDF construction ─────────────────────────────────────────────────────

    private void buildPdf(List<Map<String, Object>> loans, String department, Path outputPath)
            throws IOException {

        PDFont fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont fontOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDDocument doc = new PDDocument()) {
            // Paginate: calculate how many rows fit per page after the header block
            int rowsPerPage = (int) ((HEADER_Y - 60) / ROW_HEIGHT) - 1;
            int totalPages  = (int) Math.ceil((double) loans.size() / rowsPerPage);

            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);

                List<Map<String, Object>> pageLoans = loans.subList(
                        pageNum * rowsPerPage,
                        Math.min((pageNum + 1) * rowsPerPage, loans.size()));

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    drawPageHeader(cs, fontBold, fontOblique, department, pageNum + 1, totalPages, loans.size());
                    drawTableHeader(cs, fontBold);
                    drawTableRows(cs, fontRegular, fontBold, pageLoans, pageNum * rowsPerPage);

                    if (pageNum == totalPages - 1) {
                        drawSummary(cs, fontBold, fontRegular, loans,
                                HEADER_Y - ROW_HEIGHT * (pageLoans.size() + 2) - 10);
                    }

                    drawFooter(cs, fontOblique);
                }
            }

            doc.save(outputPath.toFile());
        }
    }

    private void drawPageHeader(PDPageContentStream cs, PDFont fontBold, PDFont fontOblique,
                                String department, int pageNum, int totalPages, int totalRecords)
            throws IOException {

        // Bank name
        cs.beginText();
        cs.setFont(fontBold, 16);
        cs.newLineAtOffset(40, PAGE_HEIGHT - 45);
        cs.showText("NATIONAL BANK — LOAN STATUS REPORT");
        cs.endText();

        // Sub-line
        String dept = (department != null && !department.isBlank() && !department.equalsIgnoreCase("null"))
                ? "Department: " + department
                : "All Departments";
        String generated = "Generated: " + LocalDateTime.now().format(DATE_FMT)
                + "   |   Records: " + totalRecords
                + "   |   Page " + pageNum + " of " + totalPages;

        cs.beginText();
        cs.setFont(fontOblique, 9);
        cs.newLineAtOffset(40, PAGE_HEIGHT - 60);
        cs.showText(dept + "   |   " + generated);
        cs.endText();

        // Separator line
        cs.moveTo(40, PAGE_HEIGHT - 68);
        cs.lineTo(PAGE_WIDTH - 40, PAGE_HEIGHT - 68);
        cs.setLineWidth(1.5f);
        cs.stroke();
    }

    private void drawTableHeader(PDPageContentStream cs, PDFont fontBold) throws IOException {
        float y = HEADER_Y;

        // Header background (filled rectangle)
        cs.setNonStrokingColor(0.15f, 0.31f, 0.54f);  // dark navy
        cs.addRect(40, y - 4, PAGE_WIDTH - 80, ROW_HEIGHT);
        cs.fill();
        cs.setNonStrokingColor(0, 0, 0);  // reset to black

        // Header text (white)
        cs.setNonStrokingColor(1f, 1f, 1f);
        for (int i = 0; i < COL_HEADERS.length; i++) {
            cs.beginText();
            cs.setFont(fontBold, 9);
            cs.newLineAtOffset(COL_X[i] + 3, y + 4);
            cs.showText(COL_HEADERS[i]);
            cs.endText();
        }
        cs.setNonStrokingColor(0, 0, 0);

        // Bottom border
        cs.moveTo(40, y - 4);
        cs.lineTo(PAGE_WIDTH - 40, y - 4);
        cs.setLineWidth(0.5f);
        cs.stroke();
    }

    private void drawTableRows(PDPageContentStream cs, PDFont fontRegular, PDFont fontBold,
                               List<Map<String, Object>> loans, int startIndex) throws IOException {
        float y = HEADER_Y - ROW_HEIGHT;

        for (int i = 0; i < loans.size(); i++) {
            Map<String, Object> loan = loans.get(i);

            // Alternating row background
            if ((startIndex + i) % 2 == 1) {
                cs.setNonStrokingColor(0.94f, 0.96f, 0.99f);
                cs.addRect(40, y - 4, PAGE_WIDTH - 80, ROW_HEIGHT);
                cs.fill();
                cs.setNonStrokingColor(0, 0, 0);
            }

            String status = safeStr(loan.get("STATUS"));
            // Colour status text
            switch (status.toUpperCase()) {
                case "ACTIVE"    -> cs.setNonStrokingColor(0.05f, 0.55f, 0.23f);  // green
                case "DEFAULTED" -> cs.setNonStrokingColor(0.8f, 0.1f, 0.1f);     // red
                default          -> cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);     // grey for CLOSED
            }

            String[] values = {
                safeStr(loan.get("LOAN_ID")),
                truncate(safeStr(loan.get("CUSTOMER_NAME")), 22),
                safeStr(loan.get("LOAN_TYPE")),
                formatAmount(loan.get("PRINCIPAL_AMOUNT")),
                formatAmount(loan.get("OUTSTANDING_AMOUNT")),
                status
            };

            for (int col = 0; col < values.length; col++) {
                // Use bold for status column
                PDFont f = (col == 5) ? fontBold : fontRegular;
                cs.beginText();
                cs.setFont(f, 8.5f);
                cs.newLineAtOffset(COL_X[col] + 3, y + 4);
                cs.showText(values[col]);
                cs.endText();
            }
            cs.setNonStrokingColor(0, 0, 0);

            // Row separator
            cs.moveTo(40, y - 4);
            cs.lineTo(PAGE_WIDTH - 40, y - 4);
            cs.setLineWidth(0.3f);
            cs.stroke();

            y -= ROW_HEIGHT;
        }

        // Outer border
        cs.addRect(40, y + ROW_HEIGHT - 4, PAGE_WIDTH - 80,
                HEADER_Y - y - ROW_HEIGHT + ROW_HEIGHT + 4);
        cs.setLineWidth(1f);
        cs.stroke();
    }

    private void drawSummary(PDPageContentStream cs, PDFont fontBold, PDFont fontRegular,
                             List<Map<String, Object>> loans, float y) throws IOException {
        long active    = loans.stream().filter(l -> "ACTIVE".equalsIgnoreCase(safeStr(l.get("STATUS")))).count();
        long closed    = loans.stream().filter(l -> "CLOSED".equalsIgnoreCase(safeStr(l.get("STATUS")))).count();
        long defaulted = loans.stream().filter(l -> "DEFAULTED".equalsIgnoreCase(safeStr(l.get("STATUS")))).count();

        double totalOutstanding = loans.stream()
                .mapToDouble(l -> toDouble(l.get("OUTSTANDING_AMOUNT")))
                .sum();

        float summaryY = y - 30;

        cs.beginText();
        cs.setFont(fontBold, 10);
        cs.newLineAtOffset(40, summaryY);
        cs.showText("Summary");
        cs.endText();

        cs.moveTo(40, summaryY - 3);
        cs.lineTo(200, summaryY - 3);
        cs.setLineWidth(0.5f);
        cs.stroke();

        String[] lines = {
            "Total Loans: " + loans.size(),
            "Active: " + active + "   |   Closed: " + closed + "   |   Defaulted: " + defaulted,
            "Total Outstanding: " + CURRENCY.format(totalOutstanding)
        };

        float lineY = summaryY - 16;
        for (String line : lines) {
            cs.beginText();
            cs.setFont(fontRegular, 9);
            cs.newLineAtOffset(40, lineY);
            cs.showText(line);
            cs.endText();
            lineY -= 14;
        }
    }

    private void drawFooter(PDPageContentStream cs, PDFont fontOblique) throws IOException {
        cs.moveTo(40, 40);
        cs.lineTo(PAGE_WIDTH - 40, 40);
        cs.setLineWidth(0.5f);
        cs.stroke();

        cs.beginText();
        cs.setFont(fontOblique, 8);
        cs.newLineAtOffset(40, 28);
        cs.showText("CONFIDENTIAL — For internal use only. Generated by National Bank AI Agent System.");
        cs.endText();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Path ensureReportDirectory() throws IOException {
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "bank-reports");
        Files.createDirectories(dir);
        return dir;
    }

    private String safeStr(Object obj) {
        return obj == null ? "" : obj.toString().trim();
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private String formatAmount(Object obj) {
        if (obj == null) return "-";
        try {
            double val = toDouble(obj);
            return String.format("%,.0f", val);
        } catch (NumberFormatException e) {
            return obj.toString();
        }
    }

    private double toDouble(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof BigDecimal bd) return bd.doubleValue();
        if (obj instanceof Number n) return n.doubleValue();
        return Double.parseDouble(obj.toString());
    }
}
