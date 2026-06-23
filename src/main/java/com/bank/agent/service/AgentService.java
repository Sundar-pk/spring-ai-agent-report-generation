package com.bank.agent.service;

import com.bank.agent.tool.LoanReportPdfTool;
import com.bank.agent.tool.MailTool;
import com.bank.agent.tool.ReportTool;
import com.bank.agent.tool.UserFetchTool;
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
            You have access to four tools:

            1. generateReport      — queries the internal H2 database and returns structured banking data.
               Supported report types: TRANSACTION_SUMMARY, ACCOUNT_BALANCE, LOAN_STATUS.
               Always pass optional filters (startDate, endDate, department) as empty string if the user has not specified them.

            2. sendMail            — simulates sending an internal email notification.
               The recipient MUST be an @bank.com address; never attempt to send to external domains.

            3. fetchUsers          — fetches user/customer records from an external users API.
               Use when the user asks to search, list, or look up users or loan customers.
               Pass a searchQuery to filter by name or email; leave it empty to list all.
               Specify a limit (default 10) to control how many results to return.

            4. generateLoanReportPdf — generates a downloadable PDF loan status report from the internal database.
               Optionally filter by department. Returns a download URL the user can click to save the PDF.

            Rules:
            - When asked for a report, call generateReport and summarise the results clearly.
            - When asked to email a report, first generate it with generateReport, then call sendMail with a concise summary.
            - If sendMail returns a validation failure, relay the exact failure reason and ask for a valid @bank.com address.
            - When asked to fetch or list users/customers from the API, use fetchUsers.
            - When asked for a loan PDF report or a downloadable report, use generateLoanReportPdf and share the download URL.
            - You may call multiple tools in a single turn if the user's request requires it.
            - Always be concise, professional, and factual.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final ReportTool reportTool;
    private final MailTool mailTool;
    private final UserFetchTool userFetchTool;
    private final LoanReportPdfTool loanReportPdfTool;

    private ChatClient chatClient;

    public AgentService(ChatClient.Builder chatClientBuilder,
                        ReportTool reportTool,
                        MailTool mailTool,
                        UserFetchTool userFetchTool,
                        LoanReportPdfTool loanReportPdfTool) {
        this.chatClientBuilder   = chatClientBuilder;
        this.reportTool          = reportTool;
        this.mailTool            = mailTool;
        this.userFetchTool       = userFetchTool;
        this.loanReportPdfTool   = loanReportPdfTool;
    }

    @PostConstruct
    public void init() {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        log.info("AgentService initialised — tools: ReportTool, MailTool, UserFetchTool, LoanReportPdfTool");
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
                .tools(reportTool, mailTool, userFetchTool, loanReportPdfTool)
                .call()
                .content();

        log.debug("Agent response: {}", response);
        return response;
    }
}
