package com.bank.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailToolTest {

    private MailTool mailTool;

    @BeforeEach
    void setUp() {
        mailTool = new MailTool();
    }

    @Test
    void validBankEmail_returnsSuccess() {
        String result = mailTool.sendMail("john.doe@bank.com", "Test message");
        assertThat(result).contains("successfully sent").contains("john.doe@bank.com");
    }

    @Test
    void externalEmail_rejected() {
        String result = mailTool.sendMail("user@gmail.com", "Test message");
        assertThat(result).contains("Mail delivery failed").contains("@bank.com");
    }

    @Test
    void subDomainEmail_rejected() {
        String result = mailTool.sendMail("user@hr.bank.com", "Test message");
        assertThat(result).contains("Mail delivery failed");
    }

    @Test
    void emptyEmail_rejected() {
        String result = mailTool.sendMail("", "Test message");
        assertThat(result).contains("Mail delivery failed");
    }

    @Test
    void emailWithSpace_rejected() {
        String result = mailTool.sendMail("john doe@bank.com", "Test message");
        assertThat(result).contains("Mail delivery failed");
    }

    @Test
    void emptyBody_rejected() {
        String result = mailTool.sendMail("john@bank.com", "");
        assertThat(result).contains("Mail delivery failed");
    }

    @Test
    void emailWithNoAtSign_rejected() {
        String result = mailTool.sendMail("johnbank.com", "Test message");
        assertThat(result).contains("Mail delivery failed");
    }

    @Test
    void emailWithSpecialCharsInLocal_rejected() {
        String result = mailTool.sendMail("john#doe@bank.com", "Test message");
        assertThat(result).contains("Mail delivery failed");
    }
}
