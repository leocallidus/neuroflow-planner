package com.example.neuroflowplanner.service.imagejob;

/**
 * Persisted snapshot of an image generation job.
 */
public class ImageJobSnapshot {
    private String jobId = "";
    private String conversationId = "";
    private String prompt = "";
    private String requestedModel = "";
    private String activeModel = "";
    private String size = "";
    private String aspectRatio = "";
    private String resolution = "";
    private String requestId = "";
    private String remoteUrl = "";
    private String savedPath = "";
    private ImageJobState state = ImageJobState.QUEUED;
    private String lastMessage = "";
    private String lastError = "";
    private long createdAt;
    private long updatedAt;
    private int attempt = 1;
    private int userRetryCount;
    private boolean pauseRequested;
    private boolean cancelRequested;

    public ImageJobSnapshot() {
    }

    public ImageJobSnapshot(ImageJobSnapshot other) {
        if (other == null) {
            return;
        }
        this.jobId = other.jobId;
        this.conversationId = other.conversationId;
        this.prompt = other.prompt;
        this.requestedModel = other.requestedModel;
        this.activeModel = other.activeModel;
        this.size = other.size;
        this.aspectRatio = other.aspectRatio;
        this.resolution = other.resolution;
        this.requestId = other.requestId;
        this.remoteUrl = other.remoteUrl;
        this.savedPath = other.savedPath;
        this.state = other.state;
        this.lastMessage = other.lastMessage;
        this.lastError = other.lastError;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.attempt = other.attempt;
        this.userRetryCount = other.userRetryCount;
        this.pauseRequested = other.pauseRequested;
        this.cancelRequested = other.cancelRequested;
    }

    public ImageJobSnapshot copy() {
        return new ImageJobSnapshot(this);
    }

    public boolean canResume() {
        return requestId != null
            && !requestId.isBlank()
            && state != null
            && state.canResumeFromRequestId()
            && !cancelRequested;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = normalize(jobId);
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = normalize(conversationId);
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = normalize(prompt);
    }

    public String getRequestedModel() {
        return requestedModel;
    }

    public void setRequestedModel(String requestedModel) {
        this.requestedModel = normalize(requestedModel);
    }

    public String getActiveModel() {
        return activeModel;
    }

    public void setActiveModel(String activeModel) {
        this.activeModel = normalize(activeModel);
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = normalize(size);
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(String aspectRatio) {
        this.aspectRatio = normalize(aspectRatio);
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = normalize(resolution);
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = normalize(requestId);
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = normalize(remoteUrl);
    }

    public String getSavedPath() {
        return savedPath;
    }

    public void setSavedPath(String savedPath) {
        this.savedPath = normalize(savedPath);
    }

    public ImageJobState getState() {
        return state;
    }

    public void setState(ImageJobState state) {
        this.state = state == null ? ImageJobState.QUEUED : state;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = normalize(lastMessage);
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = normalize(lastError);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = Math.max(0L, createdAt);
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = Math.max(0L, updatedAt);
    }

    public int getUserRetryCount() {
        return userRetryCount;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = Math.max(1, attempt);
    }

    public void setUserRetryCount(int userRetryCount) {
        this.userRetryCount = Math.max(0, userRetryCount);
    }

    public boolean isPauseRequested() {
        return pauseRequested;
    }

    public void setPauseRequested(boolean pauseRequested) {
        this.pauseRequested = pauseRequested;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
