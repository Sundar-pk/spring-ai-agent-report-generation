package com.bank.agent.controller;

import com.bank.agent.model.AgentRequest;
import com.bank.agent.model.AgentResponse;
import com.bank.agent.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST entry point for the AI Agent.
 *
 * POST /api/agent/query
 * Body : { "prompt": "Generate a transaction summary report" }
 * Returns: { "answer": "Here is the Transaction Summary..." }
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/query")
    public ResponseEntity<AgentResponse> query(@RequestBody AgentRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AgentResponse("Prompt must not be empty."));
        }

        log.info("Received agent query: {}", request.prompt());
        String answer = agentService.processQuery(request.prompt());
        return ResponseEntity.ok(new AgentResponse(answer));
    }

    /** Simple health-check endpoint for demo use. */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Agent is running.");
    }
}
