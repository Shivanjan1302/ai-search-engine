package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.AskQuestionRequest;
import com.dronzer.aisearch.dto.RagResponse;
import com.dronzer.aisearch.service.RagService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import com.dronzer.aisearch.query.ConversationContext;

import java.util.Optional;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public RagResponse askQuestion(
            @Valid @RequestBody AskQuestionRequest request,
            @AuthenticationPrincipal String email) {
        if (request.recentTurns() == null || request.recentTurns().isEmpty()) {
            return ragService.askQuestion(request.question(), email);
        }
        ConversationContext context = ConversationContext.fromRecentTurns(request.recentTurns());
        return ragService.askQuestion(request.question(), email, Optional.of(context));
    }
}
