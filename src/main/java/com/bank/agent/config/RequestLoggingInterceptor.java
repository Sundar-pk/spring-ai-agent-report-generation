package com.bank.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Logs the full URL and request body BEFORE every HTTP call reaches the LLM.
 * Helps diagnose endpoint-path issues, proxy routing, and payload differences.
 *
 * Activate with: logging.level.com.bank.agent.config=DEBUG
 */
public class RequestLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        logRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        logResponseStatus(request, response);
        return response;
    }

    private void logRequest(HttpRequest request, byte[] body) {
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║  LLM REQUEST");
        log.info("║  Method : {}", request.getMethod());
        log.info("║  URL    : {}", request.getURI());
        log.info("║  Headers: {}", request.getHeaders());
        if (body != null && body.length > 0) {
            log.info("║  Body   : {}", new String(body, StandardCharsets.UTF_8));
        }
        log.info("╚══════════════════════════════════════════════════════════════");
    }

    private void logResponseStatus(HttpRequest request, ClientHttpResponse response) {
        try {
            log.info("▶ LLM RESPONSE status: {} for {}", response.getStatusCode(), request.getURI());
        } catch (IOException e) {
            log.warn("Could not read response status: {}", e.getMessage());
        }
    }
}
