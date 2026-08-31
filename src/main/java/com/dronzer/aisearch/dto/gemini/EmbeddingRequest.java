package com.dronzer.aisearch.dto.gemini;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EmbeddingRequest {

    private String taskType;

    private Content content;

    @JsonProperty("output_dimensionality")
    private Integer outputDimensionality;

    public EmbeddingRequest() {
    }

    public EmbeddingRequest(
            String taskType,
            Content content,
            Integer outputDimensionality) {

        this.taskType = taskType;
        this.content = content;
        this.outputDimensionality = outputDimensionality;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public Integer getOutputDimensionality() {
        return outputDimensionality;
    }

    public void setOutputDimensionality(Integer outputDimensionality) {
        this.outputDimensionality = outputDimensionality;
    }
}
