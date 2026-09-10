package com.dronzer.aisearch.dto;

import jakarta.validation.constraints.NotBlank;

public record AskQuestionRequest(
	@NotBlank(message = "question must not be blank") String question) {
}
