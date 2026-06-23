package com.bank.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AI Tool: fetches user records from an external REST API (dummyjson.com/users).
 * Demonstrates how an AI Agent can dynamically build and execute outbound HTTP calls.
 */
@Component
public class UserFetchTool {

    private static final Logger log = LoggerFactory.getLogger(UserFetchTool.class);
    private static final String BASE_URL = "https://dummyjson.com/users";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Called by the LLM to fetch user records from the external API.
     *
     * @param searchQuery Keyword to search users by name/email. Pass empty string to fetch all.
     * @param limit       Maximum number of users to return (1-100). Use 10 if not specified.
     * @return JSON string containing the list of users from the external API.
     */
    @Tool(description = """
            Fetches user records from an external users API (dummyjson.com).
            Use this when the user asks to look up, search, or list users/customers.
            Pass a searchQuery to filter by name or email; leave empty to fetch all users.
            Use the limit parameter to control how many results are returned (default 10, max 100).
            Returns a JSON list of users with name, email, phone, address, and company details.
            """)
    public String fetchUsers(String searchQuery, int limit) {
        int safeLimit = (limit <= 0 || limit > 100) ? 10 : limit;
        String url = buildUrl(searchQuery, safeLimit);

        log.debug("UserFetchTool calling: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            log.debug("UserFetchTool response status: {}", statusCode);

            if (statusCode == 200) {
                return response.body();
            } else {
                return String.format(
                        "{\"error\": \"External API returned HTTP %d\", \"url\": \"%s\"}",
                        statusCode, url);
            }

        } catch (Exception e) {
            log.error("UserFetchTool failed to call {}: {}", url, e.getMessage());
            return String.format(
                    "{\"error\": \"Failed to fetch users: %s\"}", e.getMessage());
        }
    }

    private String buildUrl(String searchQuery, int limit) {
        boolean hasSearch = searchQuery != null && !searchQuery.isBlank()
                && !searchQuery.equalsIgnoreCase("null");

        if (hasSearch) {
            // Search endpoint: /users/search?q=<term>&limit=<n>
            return BASE_URL + "/search?q=" + encodeParam(searchQuery) + "&limit=" + limit;
        } else {
            // List all endpoint: /users?limit=<n>
            return BASE_URL + "?limit=" + limit;
        }
    }

    private String encodeParam(String value) {
        return value.trim().replace(" ", "%20");
    }
}
