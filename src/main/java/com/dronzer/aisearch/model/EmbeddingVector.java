package com.dronzer.aisearch.model;

import java.util.List;

public class EmbeddingVector {

    private final List<Float> values;

    public EmbeddingVector(List<Float> values) {
        this.values = values;
    }

    public List<Float> getValues() {
        return values;
    }

    public int size() {
        return values.size();
    }
}