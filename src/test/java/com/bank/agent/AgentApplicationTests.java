package com.bank.agent;

import org.junit.jupiter.api.Test;

/**
 * Minimal sanity test — does not start the full Spring context
 * (avoids needing a real LLM API key during CI/build).
 */
class AgentApplicationTests {

    @Test
    void contextLoads() {
        // Spring context start requires a live LLM endpoint.
        // Unit-level tool tests are in ReportToolTest and MailToolTest.
    }
}
