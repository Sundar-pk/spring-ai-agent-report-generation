package com.bank.agent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> getTransactionSummary(String startDate, String endDate, String department) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.DEPARTMENT,
                       t.TRANSACTION_TYPE,
                       COUNT(*)              AS TRANSACTION_COUNT,
                       SUM(t.AMOUNT)         AS TOTAL_AMOUNT,
                       MIN(t.TRANSACTION_DATE) AS EARLIEST_DATE,
                       MAX(t.TRANSACTION_DATE) AS LATEST_DATE
                FROM TRANSACTIONS t
                WHERE 1 = 1
                """);

        List<Object> params = new ArrayList<>();

        if (isPresent(startDate)) {
            sql.append(" AND t.TRANSACTION_DATE >= PARSEDATETIME(?, 'yyyy-MM-dd') ");
            params.add(startDate);
        }
        if (isPresent(endDate)) {
            sql.append(" AND t.TRANSACTION_DATE <= PARSEDATETIME(?, 'yyyy-MM-dd') ");
            params.add(endDate);
        }
        if (isPresent(department)) {
            sql.append(" AND LOWER(t.DEPARTMENT) = LOWER(?) ");
            params.add(department);
        }

        sql.append(" GROUP BY t.DEPARTMENT, t.TRANSACTION_TYPE ORDER BY t.DEPARTMENT, t.TRANSACTION_TYPE");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getAccountBalances(String department) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.ACCOUNT_NUMBER,
                       a.CUSTOMER_NAME,
                       a.ACCOUNT_TYPE,
                       a.BALANCE,
                       a.DEPARTMENT,
                       a.OPENED_DATE
                FROM ACCOUNTS a
                WHERE 1 = 1
                """);

        List<Object> params = new ArrayList<>();

        if (isPresent(department)) {
            sql.append(" AND LOWER(a.DEPARTMENT) = LOWER(?) ");
            params.add(department);
        }

        sql.append(" ORDER BY a.BALANCE DESC");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getLoanStatus(String department) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.LOAN_ID,
                       l.ACCOUNT_NUMBER,
                       a.CUSTOMER_NAME,
                       l.LOAN_TYPE,
                       l.PRINCIPAL_AMOUNT,
                       l.OUTSTANDING_AMOUNT,
                       l.STATUS,
                       l.DEPARTMENT
                FROM LOANS l
                JOIN ACCOUNTS a ON l.ACCOUNT_NUMBER = a.ACCOUNT_NUMBER
                WHERE 1 = 1
                """);

        List<Object> params = new ArrayList<>();

        if (isPresent(department)) {
            sql.append(" AND LOWER(l.DEPARTMENT) = LOWER(?) ");
            params.add(department);
        }

        sql.append(" ORDER BY l.STATUS, l.OUTSTANDING_AMOUNT DESC");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank() && !value.equalsIgnoreCase("null");
    }
}
