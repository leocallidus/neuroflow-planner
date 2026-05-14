package com.example.neuroflowplanner.model;

public class ImageJobRecord {
    private final String jobId;
    private final String conversationId;
    private final String requestId;
    private final String requestedModel;
    private final String activeModel;
    private final String prompt;
    private final String promptHash;
    private final String size;
    private final String aspectRatio;
    private final String resolution;
    private final String stage;
    private final int attempt;
    private final int userRetryCount;
    private final String remoteUrl;
    private final String savedPath;
    private final String lastMessage;
    private final String lastError;
    private final boolean pauseRequested;
    private final boolean cancelRequested;
    private final String createdAt;
    private final String updatedAt;

    public ImageJobRecord(
        String jobId,
        String conversationId,
        String requestId,
        String requestedModel,
        String activeModel,
        String prompt,
        String promptHash,
        String size,
        String aspectRatio,
        String resolution,
        String stage,
        int attempt,
        int userRetryCount,
        String remoteUrl,
        String savedPath,
        String lastMessage,
        String lastError,
        boolean pauseRequested,
        boolean cancelRequested,
        String createdAt,
        String updatedAt
    ) {
        this.jobId = jobId;
        this.conversationId = conversationId;
        this.requestId = requestId;
        this.requestedModel = requestedModel;
        this.activeModel = activeModel;
        this.prompt = prompt;
        this.promptHash = promptHash;
        this.size = size;
        this.aspectRatio = aspectRatio;
        this.resolution = resolution;
        this.stage = stage;
        this.attempt = attempt;
        this.userRetryCount = userRetryCount;
        this.remoteUrl = remoteUrl;
        this.savedPath = savedPath;
        this.lastMessage = lastMessage;
        this.lastError = lastError;
        this.pauseRequested = pauseRequested;
        this.cancelRequested = cancelRequested;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getJobId() {
        return jobId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRequestedModel() {
        return requestedModel;
    }

    public String getActiveModel() {
        return activeModel;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public String getSize() {
        return size;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public String getResolution() {
        return resolution;
    }

    public String getStage() {
        return stage;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getUserRetryCount() {
        return userRetryCount;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public String getSavedPath() {
        return savedPath;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isPauseRequested() {
        return pauseRequested;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
