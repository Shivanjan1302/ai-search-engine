package com.dronzer.aisearch.dto;

import com.dronzer.aisearch.query.ConversationContext;
import com.dronzer.aisearch.query.ConversationContext.Turn;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AskQuestionRequest(
        @NotBlank(message = "question must not be blank") String question,
        @Valid
        @Size(max = ConversationContext.MAX_RECENT_TURNS,
                message = "recentTurns must not exceed " + ConversationContext.MAX_RECENT_TURNS + " turns")
        List<@NotNull(message = "recentTurns must not contain null turns")
                Turn> recentTurns) {

    public AskQuestionRequest(String question) {
        this(question, null);
    }
}
