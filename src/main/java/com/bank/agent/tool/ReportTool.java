package com.bank.agent.tool;

import com.bank.agent.repository.ReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI Tool: generates banking reports by querying the H2 database.
 * Registered as a Spring bean and exposed to the LLM via @Tool.
 */
@Component
public class ReportTool {

    private static final Logger log = LoggerFactory.getLogger(ReportTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReportRepository reportRepository;

    public ReportTool(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Called by the LLM to generate a banking report from the H2 database.
     *
     * @param reportType  One of: TRANSACTION_SUMMARY, ACCOUNT_BALANCE, LOAN_STATUS
     * @param startDate   Optional start date filter (yyyy-MM-dd). Pass empty or null if not needed.
     * @param endDate     Optional end date filter (yyyy-MM-dd). Pass empty or null if not needed.
     * @param department  Optional department/branch filter. Pass empty or null if not needed.
     * @return JSON string containing the report data.
     */
    @Tool(description = """
            Generates a banking report from the internal H2 database.
            Supported report types: TRANSACTION_SUMMARY, ACCOUNT_BALANCE, LOAN_STATUS.
            Optional filters: startDate (yyyy-MM-dd), endDate (yyyy-MM-dd), department name.
            Pass empty string or null for optional filters when not specified by the user.
            """)
    public String generateReport(String reportType, String startDate, String endDate, String department) {
        log.debug("ReportTool invoked: type={}, startDate={}, endDate={}, department={}",
                reportType, startDate, endDate, department);

        if (reportType == null || reportType.isBlank()) {
            return """
                    {"error": "reportType is required. Supported values: TRANSACTION_SUMMARY, ACCOUNT_BALANCE, LOAN_STATUS"}
                    """;
        }

        try {
            List<Map<String, Object>> data = switch (reportType.trim().toUpperCase()) {
                case "TRANSACTION_SUMMARY" -> reportRepository.getTransactionSummary(startDate, endDate, department);
                case "ACCOUNT_BALANCE"     -> reportRepository.getAccountBalances(department);
                case "LOAN_STATUS"         -> reportRepository.getLoanStatus(department);
                default -> null;
            };

            if (data == null) {
                return String.format(
                        "{\"error\": \"Unknown report type '%s'. Supported: TRANSACTION_SUMMARY, ACCOUNT_BALANCE, LOAN_STATUS\"}",
                        reportType);
            }

            if (data.isEmpty()) {
                return String.format(
                        "{\"reportType\": \"%s\", \"message\": \"No records found for the given filters.\", \"records\": []}",
                        reportType);
            }

            Map<String, Object> result = Map.of(
                    "reportType", reportType.toUpperCase(),
                    "recordCount", data.size(),
                    "filters", buildFilterSummary(startDate, endDate, department),
                    "records", data
            );

            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            log.debug("ReportTool result: {} records returned for {}", data.size(), reportType);
            return json;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialise report result", e);
            return "{\"error\": \"Internal error while generating report.\"}";
        }
    }

    private String buildFilterSummary(String startDate, String endDate, String department) {
        StringBuilder sb = new StringBuilder("{");
        if (isPresent(startDate))   sb.append("\"startDate\":\"").append(startDate).append("\",");
        if (isPresent(endDate))     sb.append("\"endDate\":\"").append(endDate).append("\",");
        if (isPresent(department))  sb.append("\"department\":\"").append(department).append("\",");
        if (sb.length() > 1) sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank() && !value.equalsIgnoreCase("null");
    }
}
