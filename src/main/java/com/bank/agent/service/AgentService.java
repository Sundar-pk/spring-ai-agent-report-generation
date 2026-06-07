package com.bank.agent.service;

import com.bank.agent.tool.MailTool;
import com.bank.agent.tool.ReportTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the AI Agent: builds the ChatClient with the system prompt and
 * registered tools, then drives the agentic tool-call loop for each user query.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String SYSTEM_PROMPT = """
            You are a helpful banking assistant for an internal bank IT system.
            You have access to two tools:

            1. generateReport  — queries the internal H2 database and returns structured banking data.
               Supported report types: TRANSACTION_SUMMARY, ACCOUNT_BALANCE, LOAN_STATUS.
               Always pass optional filters (startDate, endDate, department) as empty string if the user has not specified them.

            2. sendMail        — simulates sending an internal email notification.
               The recipient MUST be an @bank.com address; never attempt to send to external domains.

            When a user asks for a report, call generateReport and summarise the results clearly.
            When a user asks to email a report, first generate the report, then call sendMail with a concise summary as the body.
            If sendMail returns a validation failure, relay the exact failure reason to the user and ask for a valid @bank.com address.
            Always be concise, professional, and factual.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ReportTool reportTool;
    private final MailTool mailTool;

    private ChatClient chatClient;

    public AgentService(ChatClient.Builder chatClientBuilder,
                        ReportTool reportTool,
                        MailTool mailTool) {
        this.chatClientBuilder = chatClientBuilder;
        this.reportTool = reportTool;
        this.mailTool = mailTool;
    }

    @PostConstruct
    public void init() {
        // Build the ChatClient once; tool registration happens per-prompt via .tools()
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        log.info("AgentService initialised — tools registered: ReportTool, MailTool");
    }

    /**
     * Processes a natural-language user prompt through the AI Agent.
     * The LLM will autonomously decide which tool(s) to call, call them,
     * and produce a final natural-language answer.
     *
     * @param prompt The user's natural-language request.
     * @return The agent's final response after all tool calls are resolved.
     */
    public String processQuery(String prompt) {
        log.debug("Processing agent query: {}", prompt);

        String response = chatClient.prompt()
                .user(prompt)
                .tools(reportTool, mailTool)
                .call()
                .content();

        log.debug("Agent response: {}", response);
        return response;
    }
}
