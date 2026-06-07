package com.bank.agent.config;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import javax.net.ssl.SSLContext;
import java.net.URI;

/**
 * Configures the shared HTTP client used by Spring AI's RestClient to reach the LLM:
 *
 *  1. SSL verification disabled  — mirrors JS { rejectUnauthorized: false }
 *     Internal LLM servers often use self-signed certificates.
 *
 *  2. Proxy with Basic Auth       — mirrors JS { proxy: "http://user:pwd@host:port" }
 *     Only applied when LLM_PROXY_URL environment variable is set.
 *
 *  3. Request/response logging    — logs full URL + body before every LLM call.
 */
@Configuration
public class HttpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(HttpClientConfig.class);

    /**
     * Proxy URL in the format: http://username:password@host:port
     * Set via environment variable LLM_PROXY_URL.
     * Leave unset / empty to skip proxy configuration.
     */
    @Value("${LLM_PROXY_URL:}")
    private String proxyUrl;

    /**
     * Registers a RestClientCustomizer bean.
     * Spring AI's OpenAI auto-configuration injects Spring Boot's RestClient.Builder,
     * which automatically applies all RestClientCustomizer beans — so this customizer
     * is transparently picked up by the LLM client.
     */
    @Bean
    public RestClientCustomizer llmRestClientCustomizer() {
        return builder -> {
            try {
                HttpClient httpClient = buildHttpClient();
                HttpComponentsClientHttpRequestFactory factory =
                        new HttpComponentsClientHttpRequestFactory(httpClient);

                builder.requestFactory(factory)
                       .requestInterceptor(new RequestLoggingInterceptor());

                log.info("LLM HTTP client configured — SSL verification: DISABLED, Proxy: {}",
                        proxyUrl.isBlank() ? "NONE" : maskCredentials(proxyUrl));

            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure LLM HTTP client", e);
            }
        };
    }

    // ── Private builders ──────────────────────────────────────────────────────

    private HttpClient buildHttpClient() throws Exception {
        SSLContext sslContext = buildTrustAllSslContext();
        HttpClientConnectionManager connectionManager = buildConnectionManager(sslContext);
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling();

        if (!proxyUrl.isBlank()) {
            applyProxy(clientBuilder);
        }

        return clientBuilder.build();
    }

    /**
     * Creates an SSL context that trusts ALL certificates.
     * Equivalent to JS: { rejectUnauthorized: false, strictSSL: false }
     */
    private SSLContext buildTrustAllSslContext() throws Exception {
        return SSLContextBuilder.create()
                .loadTrustMaterial(null, (chain, authType) -> true)   // trust everything
                .build();
    }

    private HttpClientConnectionManager buildConnectionManager(SSLContext sslContext) {
        SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)   // skip hostname check
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .setMaxConnTotal(50)
                .setMaxConnPerRoute(20)
                .build();
    }

    /**
     * Parses LLM_PROXY_URL and applies proxy + Basic Auth to the client builder.
     * Format: http://username:password@host:port
     * Equivalent to JS: { proxy: "http://user:pwd@host:port" }
     */
    private void applyProxy(HttpClientBuilder clientBuilder) {
        try {
            URI uri = URI.create(proxyUrl);

            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host   = uri.getHost();
            int    port   = uri.getPort() != -1 ? uri.getPort() : 8080;

            HttpHost proxyHost = new HttpHost(scheme, host, port);
            clientBuilder.setProxy(proxyHost);

            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] parts    = userInfo.split(":", 2);
                String   username = parts[0];
                char[]   password = parts.length > 1 ? parts[1].toCharArray() : new char[0];

                BasicCredentialsProvider credProvider = new BasicCredentialsProvider();
                credProvider.setCredentials(
                        new AuthScope(proxyHost),
                        new UsernamePasswordCredentials(username, password)
                );
                clientBuilder.setDefaultCredentialsProvider(credProvider);

                log.info("Proxy applied: {}://{}@{}:{}", scheme, username, host, port);
            } else {
                log.info("Proxy applied (no auth): {}://{}:{}", scheme, host, port);
            }

        } catch (Exception e) {
            log.error("Failed to parse LLM_PROXY_URL '{}': {}", maskCredentials(proxyUrl), e.getMessage());
            throw new IllegalArgumentException("Invalid LLM_PROXY_URL format. " +
                    "Expected: http://username:password@host:port", e);
        }
    }

    /** Replaces password in proxy URL with *** for safe logging. */
    private String maskCredentials(String url) {
        return url.replaceAll("(https?://)([^:]+):([^@]+)@", "$1$2:***@");
    }
}
