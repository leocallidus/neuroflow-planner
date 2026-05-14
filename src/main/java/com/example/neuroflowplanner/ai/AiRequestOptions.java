package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.media.AiMediaInput;

import java.util.List;
import java.util.Objects;

/**
 * Options for an AI request.
 * Contains configuration for how the AI should process the request.
 */
public record AiRequestOptions(
        /**
         * The AI model to use for the request.
         * For Ollama: model name like "llama3", "mistral"
         * For OpenAI: model name like "gpt-4", "gpt-3.5-turbo"
         */
        String model,

        /**
         * System prompt that sets the AI's behavior and context.
         */
        String systemPrompt,

        /**
         * Temperature for response generation (0.0 - 2.0).
         * Lower values = more deterministic, higher = more creative.
         * null means use default.
         */
        Double temperature,

        /**
         * Maximum tokens in the response.
         * null means use default.
         */
        Integer maxTokens,

        /**
         * Optional top_p (nucleus sampling) parameter.
         */
        Double topP,

        /**
         * Optional frequency penalty (-2.0..2.0).
         */
        Double frequencyPenalty,

        /**
         * Optional presence penalty (-2.0..2.0).
         */
        Double presencePenalty,

        /**
         * Conversation history for context.
         * Each entry is a pair of (role, content) where role is "user" or "assistant".
         */
        List<ChatHistoryEntry> conversationHistory,

        /**
         * Optional reasoning effort hint for providers that support it.
         */
        String reasoningEffort,

        /**
         * Optional structured reasoning configuration for providers that support
         * reasoning tokens.
         */
        ReasoningOptions reasoning,

        /**
         * Optional plugin configuration for providers that support chat-completions plugins.
         */
        PluginOptions pluginOptions,

        /**
         * Optional multimodal inputs attached to the current user message.
         */
        List<AiMediaInput> mediaInputs,

        /**
         * Whether this is a streaming request.
         */
        boolean stream) {
    /**
     * Creates options with defaults.
     */
    public AiRequestOptions {
        // Defensive copy of conversation history
        if (conversationHistory != null) {
            conversationHistory = List.copyOf(conversationHistory);
        }
        if (mediaInputs != null) {
            mediaInputs = List.copyOf(mediaInputs);
        }
    }

    public AiRequestOptions(
            String model,
            String systemPrompt,
            Double temperature,
            Integer maxTokens,
            Double topP,
            Double frequencyPenalty,
            Double presencePenalty,
            List<ChatHistoryEntry> conversationHistory,
            String reasoningEffort,
            ReasoningOptions reasoning,
            boolean stream) {
        this(model, systemPrompt, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, conversationHistory, reasoningEffort, reasoning, null, null, stream);
    }

    public AiRequestOptions(
            String model,
            String systemPrompt,
            Double temperature,
            Integer maxTokens,
            Double topP,
            Double frequencyPenalty,
            Double presencePenalty,
            List<ChatHistoryEntry> conversationHistory,
            String reasoningEffort,
            ReasoningOptions reasoning,
            List<AiMediaInput> mediaInputs,
            boolean stream) {
        this(model, systemPrompt, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, conversationHistory, reasoningEffort, reasoning, null, mediaInputs, stream);
    }

    public AiRequestOptions(
            String model,
            String systemPrompt,
            Double temperature,
            Integer maxTokens,
            List<ChatHistoryEntry> conversationHistory,
            String reasoningEffort,
            ReasoningOptions reasoning,
            boolean stream) {
        this(model, systemPrompt, temperature, maxTokens, null, null, null, conversationHistory, reasoningEffort, reasoning, null, null, stream);
    }

    public AiRequestOptions(
            String model,
            String systemPrompt,
            Double temperature,
            Integer maxTokens,
            List<ChatHistoryEntry> conversationHistory,
            String reasoningEffort,
            ReasoningOptions reasoning,
            List<AiMediaInput> mediaInputs,
            boolean stream) {
        this(model, systemPrompt, temperature, maxTokens, null, null, null, conversationHistory, reasoningEffort, reasoning, null, mediaInputs, stream);
    }

    /**
     * Creates a copy of these options but with a different model.
     */
    public AiRequestOptions withModel(String newModel) {
        return new AiRequestOptions(newModel, systemPrompt, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, conversationHistory, reasoningEffort, reasoning, pluginOptions, mediaInputs, stream);
    }

    /**
     * Creates a copy of these options but with a different stream flag.
     */
    public AiRequestOptions withStream(boolean newStream) {
        return new AiRequestOptions(model, systemPrompt, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, conversationHistory, reasoningEffort, reasoning, pluginOptions, mediaInputs, newStream);
    }

    public AiRequestOptions withMediaInputs(List<AiMediaInput> newMediaInputs) {
        return new AiRequestOptions(model, systemPrompt, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, conversationHistory, reasoningEffort, reasoning, pluginOptions, newMediaInputs, stream);
    }

    /**
     * Creates a request with model and system prompt.
     */
    public static AiRequestOptions withModelAndSystem(String model, String systemPrompt) {
        return new AiRequestOptions(model, systemPrompt, null, null, null, null, null, null, false);
    }

    /**
     * Creates a builder for more complex configurations.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Represents a single entry in conversation history.
     */
    public record ChatHistoryEntry(String role, String content) {
        public ChatHistoryEntry {
            Objects.requireNonNull(role, "role cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
        }

        public static ChatHistoryEntry user(String content) {
            return new ChatHistoryEntry("user", content);
        }

        public static ChatHistoryEntry assistant(String content) {
            return new ChatHistoryEntry("assistant", content);
        }

        public static ChatHistoryEntry system(String content) {
            return new ChatHistoryEntry("system", content);
        }
    }

    public record ReasoningOptions(
            String effort,
            Integer maxTokens,
            String summary,
            Boolean enabled,
            Boolean exclude) {
    }

    public record PluginOptions(
            WebPluginOptions web,
            FileParserPluginOptions fileParser,
            ResponseHealingPluginOptions responseHealing) {
    }

    public record WebPluginOptions(
            boolean enabled,
            String engine,
            Integer maxResults,
            String searchPrompt) {
    }

    public record FileParserPluginOptions(
            boolean enabled,
            String pdfEngine) {
    }

    public record ResponseHealingPluginOptions(
            boolean enabled) {
    }

    /**
     * Builder for AiRequestOptions.
     */
    public static class Builder {
        private String model;
        private String systemPrompt;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private List<ChatHistoryEntry> conversationHistory;
        private String reasoningEffort;
        private ReasoningOptions reasoning;
        private PluginOptions pluginOptions;
        private List<AiMediaInput> mediaInputs;
        private boolean stream = false;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder conversationHistory(List<ChatHistoryEntry> history) {
            this.conversationHistory = history;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder reasoning(ReasoningOptions reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder pluginOptions(PluginOptions pluginOptions) {
            this.pluginOptions = pluginOptions;
            return this;
        }

        public Builder mediaInputs(List<AiMediaInput> mediaInputs) {
            this.mediaInputs = mediaInputs;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public AiRequestOptions build() {
            return new AiRequestOptions(model, systemPrompt, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, conversationHistory, reasoningEffort, reasoning, pluginOptions, mediaInputs, stream);
        }
    }
}
