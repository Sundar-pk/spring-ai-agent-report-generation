package com.bank.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * AI Tool: validates an internal bank email address and simulates mail dispatch.
 * No SMTP or real mail transport is used — this is a mock for demo purposes.
 */
@Component
public class MailTool {

    private static final Logger log = LoggerFactory.getLogger(MailTool.class);

    // Matches local-part (letters, digits, dots, hyphens, underscores) followed by @bank.com exactly.
    // Sub-domains (e.g. hr.bank.com) and other domains are rejected.
    private static final Pattern BANK_EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*@bank\\.com$");

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Called by the LLM to send an internal bank notification email.
     *
     * @param recipientEmail  Recipient email — must end with @bank.com.
     * @param messageBody     Body of the email to be sent.
     * @return Result string describing success or the validation failure reason.
     */
    @Tool(description = """
            Sends an internal bank email notification (mock — no real SMTP).
            The recipient email address MUST be an @bank.com address (e.g. john@bank.com).
            Addresses from any other domain, including sub-domains like hr.bank.com, are rejected.
            Provide recipientEmail and messageBody.
            """)
    public String sendMail(String recipientEmail, String messageBody) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);

        // ── Presence checks ──────────────────────────────────────────────────
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[{}] Mail dispatch rejected: recipientEmail is empty", timestamp);
            return "Mail delivery failed: recipientEmail must not be empty.";
        }

        if (messageBody == null || messageBody.isBlank()) {
            log.warn("[{}] Mail dispatch rejected: messageBody is empty for recipient={}", timestamp, recipientEmail);
            return "Mail delivery failed: messageBody must not be empty.";
        }

        String email = recipientEmail.trim();

        // ── EV-04: no spaces ─────────────────────────────────────────────────
        if (email.contains(" ")) {
            log.warn("[{}] Mail dispatch rejected: spaces found in email={}", timestamp, email);
            return "Mail delivery failed: email address must not contain spaces.";
        }

        // ── EV-01: exactly one @ ─────────────────────────────────────────────
        long atCount = email.chars().filter(c -> c == '@').count();
        if (atCount != 1) {
            log.warn("[{}] Mail dispatch rejected: invalid format email={}", timestamp, email);
            return "Mail delivery failed: email address format is invalid (must contain exactly one '@').";
        }

        // ── EV-02 + EV-03 + EV-05 + EV-06: full pattern check ───────────────
        if (!BANK_EMAIL_PATTERN.matcher(email).matches()) {
            log.warn("[{}] Mail dispatch rejected: domain restriction violated for email={}", timestamp, email);
            return "Mail delivery failed: recipient address must be an internal @bank.com address. " +
                   "External addresses and sub-domains (e.g. hr.bank.com) are not permitted.";
        }

        // ── Mock dispatch ────────────────────────────────────────────────────
        log.info("[{}] [MOCK MAIL SENT] To: {}  |  Body length: {} chars",
                timestamp, email, messageBody.length());
        log.debug("[{}] [MOCK MAIL BODY] To: {}  |  Body: {}", timestamp, email, messageBody);

        return String.format(
                "Mail successfully sent to %s at %s. " +
                "(Note: This is a simulated dispatch — no real email was transmitted.)",
                email, timestamp);
    }
}
