package com.dronzer.aisearch.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateNoteRequest {

    @NotBlank(message = "title must not be blank")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}