package com.example.neuroflowplanner.ai;

/**
 * Incremental streaming chunk emitted by AI providers that support token
 * streaming.
 */
public record AiStreamChunk(
        String contentDelta,
        String model,
        boolean done) {

    public AiStreamChunk {
        contentDelta = contentDelta == null ? "" : contentDelta;
        model = model == null ? "" : model;
    }

    public static AiStreamChunk delta(String delta, String model) {
        return new AiStreamChunk(delta, model, false);
    }

    public static AiStreamChunk done(String model) {
        return new AiStreamChunk("", model, true);
    }
}
