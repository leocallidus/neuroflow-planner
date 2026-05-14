package com.example.neuroflowplanner.service.imagejob;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.ImageJobRecord;
import com.example.neuroflowplanner.util.DataPathManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Persistent queue/state storage for image generation jobs.
 * Database is the primary store; legacy JSON is imported as fallback.
 */
public final class ImageJobManager {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ImageJobManager.class);
    private static final ImageJobManager INSTANCE = new ImageJobManager();

    private final DatabaseManager db = DatabaseManager.getInstance();
    private final ObjectMapper mapper = AiObjectMapperFactory.createMapper(false);
    private final Path legacyStoragePath = DataPathManager.getDataDirectory().resolve("image-jobs.json");

    private ImageJobManager() {
        importLegacyJsonIfNeeded();
    }

    public static ImageJobManager getInstance() {
        return INSTANCE;
    }

    public synchronized ImageJobSnapshot upsertJob(ImageJobSnapshot snapshot) {
        if (snapshot == null || snapshot.getJobId().isBlank()) {
            return null;
        }
        db.saveImageJobState(toRecord(snapshot));
        return getJob(snapshot.getJobId());
    }

    public synchronized ImageJobSnapshot getJob(String jobId) {
        ImageJobRecord record = db.loadImageJobState(normalize(jobId));
        return record == null ? null : toSnapshot(record);
    }

    public synchronized ImageJobSnapshot updateJob(String jobId, Consumer<ImageJobSnapshot> updater) {
        ImageJobSnapshot snapshot = getJob(jobId);
        if (snapshot == null) {
            return null;
        }
        if (updater != null) {
            updater.accept(snapshot);
        }
        snapshot.setUpdatedAt(System.currentTimeMillis());
        return upsertJob(snapshot);
    }

    public synchronized List<ImageJobSnapshot> listJobsForConversation(String conversationId) {
        return db.loadImageJobStatesByConversation(normalize(conversationId)).stream()
            .map(this::toSnapshot)
            .toList();
    }

    public synchronized ImageJobSnapshot findLatestJobForConversation(String conversationId) {
        ImageJobRecord record = db.loadLatestImageJobStateByConversation(normalize(conversationId));
        return record == null ? null : toSnapshot(record);
    }

    public synchronized List<ImageJobSnapshot> listResumableJobsForConversation(String conversationId) {
        String normalizedConversationId = normalize(conversationId);
        return db.loadResumableImageJobStates().stream()
            .map(this::toSnapshot)
            .filter(job -> normalizedConversationId.isBlank() || normalizedConversationId.equals(job.getConversationId()))
            .sorted(Comparator.comparingLong(ImageJobSnapshot::getUpdatedAt).reversed())
            .toList();
    }

    public synchronized List<ImageJobSnapshot> listResumableJobs() {
        return db.loadResumableImageJobStates().stream()
            .map(this::toSnapshot)
            .sorted(Comparator.comparingLong(ImageJobSnapshot::getUpdatedAt).reversed())
            .toList();
    }

    public synchronized boolean requestPause(String jobId) {
        ImageJobSnapshot snapshot = getJob(jobId);
        if (snapshot == null || snapshot.getState() == ImageJobState.DONE || snapshot.getState() == ImageJobState.CANCELLED) {
            return false;
        }
        snapshot.setPauseRequested(true);
        snapshot.setCancelRequested(false);
        snapshot.setUpdatedAt(System.currentTimeMillis());
        upsertJob(snapshot);
        return true;
    }

    public synchronized boolean requestCancel(String jobId) {
        ImageJobSnapshot snapshot = getJob(jobId);
        if (snapshot == null || snapshot.getState() == ImageJobState.DONE || snapshot.getState() == ImageJobState.CANCELLED) {
            return false;
        }
        snapshot.setCancelRequested(true);
        snapshot.setPauseRequested(false);
        snapshot.setUpdatedAt(System.currentTimeMillis());
        upsertJob(snapshot);
        return true;
    }

    public synchronized ImageJobSnapshot clearControlFlags(String jobId) {
        return updateJob(jobId, snapshot -> {
            snapshot.setPauseRequested(false);
            snapshot.setCancelRequested(false);
        });
    }

    public synchronized ImageJobSnapshot prepareUserRetry(String jobId) {
        return updateJob(jobId, snapshot -> {
            snapshot.setUserRetryCount(snapshot.getUserRetryCount() + 1);
            snapshot.setPauseRequested(false);
            snapshot.setCancelRequested(false);
            snapshot.setRequestId("");
            snapshot.setRemoteUrl("");
            snapshot.setSavedPath("");
            snapshot.setActiveModel(snapshot.getRequestedModel());
            snapshot.setState(ImageJobState.QUEUED);
            snapshot.setAttempt(1);
            snapshot.setLastMessage("Пользователь запустил повтор image-job.");
            snapshot.setLastError("");
        });
    }

    private void importLegacyJsonIfNeeded() {
        if (!Files.exists(legacyStoragePath)) {
            return;
        }
        if (db.countImageJobStates() > 0) {
            return;
        }
        try {
            StoredJobsPayload payload = mapper.readValue(legacyStoragePath.toFile(), StoredJobsPayload.class);
            if (payload == null || payload.jobs == null || payload.jobs.isEmpty()) {
                return;
            }
            int imported = 0;
            for (ImageJobSnapshot snapshot : payload.jobs) {
                if (snapshot == null || snapshot.getJobId().isBlank()) {
                    continue;
                }
                snapshot.setUpdatedAt(snapshot.getUpdatedAt() > 0L ? snapshot.getUpdatedAt() : System.currentTimeMillis());
                snapshot.setCreatedAt(snapshot.getCreatedAt() > 0L ? snapshot.getCreatedAt() : snapshot.getUpdatedAt());
                db.saveImageJobState(toRecord(snapshot));
                imported++;
            }
            LOG.info("image.jobs.legacy.json.imported", "count", imported, "storagePath", legacyStoragePath);
        } catch (IOException ex) {
            LOG.error("image.jobs.legacy.json.import.failed", ex, "storagePath", legacyStoragePath);
        }
    }

    private ImageJobRecord toRecord(ImageJobSnapshot snapshot) {
        return new ImageJobRecord(
            normalize(snapshot.getJobId()),
            normalize(snapshot.getConversationId()),
            normalize(snapshot.getRequestId()),
            normalize(snapshot.getRequestedModel()),
            normalize(snapshot.getActiveModel()),
            normalize(snapshot.getPrompt()),
            hashPrompt(snapshot.getPrompt()),
            normalize(snapshot.getSize()),
            normalize(snapshot.getAspectRatio()),
            normalize(snapshot.getResolution()),
            snapshot.getState() == null ? ImageJobState.QUEUED.name() : snapshot.getState().name(),
            Math.max(1, snapshot.getAttempt()),
            Math.max(0, snapshot.getUserRetryCount()),
            normalize(snapshot.getRemoteUrl()),
            normalize(snapshot.getSavedPath()),
            normalize(snapshot.getLastMessage()),
            normalize(snapshot.getLastError()),
            snapshot.isPauseRequested(),
            snapshot.isCancelRequested(),
            formatTimestamp(snapshot.getCreatedAt()),
            formatTimestamp(snapshot.getUpdatedAt())
        );
    }

    private ImageJobSnapshot toSnapshot(ImageJobRecord record) {
        ImageJobSnapshot snapshot = new ImageJobSnapshot();
        snapshot.setJobId(record.getJobId());
        snapshot.setConversationId(record.getConversationId());
        snapshot.setPrompt(record.getPrompt());
        snapshot.setRequestedModel(record.getRequestedModel());
        snapshot.setActiveModel(record.getActiveModel());
        snapshot.setSize(record.getSize());
        snapshot.setAspectRatio(record.getAspectRatio());
        snapshot.setResolution(record.getResolution());
        snapshot.setRequestId(record.getRequestId());
        snapshot.setRemoteUrl(record.getRemoteUrl());
        snapshot.setSavedPath(record.getSavedPath());
        snapshot.setState(parseState(record.getStage()));
        snapshot.setAttempt(record.getAttempt());
        snapshot.setLastMessage(record.getLastMessage());
        snapshot.setLastError(record.getLastError());
        snapshot.setCreatedAt(parseTimestamp(record.getCreatedAt()));
        snapshot.setUpdatedAt(parseTimestamp(record.getUpdatedAt()));
        snapshot.setUserRetryCount(record.getUserRetryCount());
        snapshot.setPauseRequested(record.isPauseRequested());
        snapshot.setCancelRequested(record.isCancelRequested());
        return snapshot;
    }

    private String hashPrompt(String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalize(prompt).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            LOG.warning("image.jobs.prompt.hash.failed", "reason", ex.getClass().getSimpleName());
            return "";
        }
    }

    private String formatTimestamp(long epochMs) {
        long normalized = epochMs > 0L ? epochMs : System.currentTimeMillis();
        return LocalDateTime.ofEpochSecond(
            normalized / 1000L,
            (int) ((normalized % 1000L) * 1_000_000L),
            java.time.ZoneOffset.UTC
        ).toString();
    }

    private long parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return LocalDateTime.parse(raw.trim()).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ex) {
            return 0L;
        }
    }

    private ImageJobState parseState(String raw) {
        if (raw == null || raw.isBlank()) {
            return ImageJobState.QUEUED;
        }
        try {
            return ImageJobState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ImageJobState.QUEUED;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    private static final class StoredJobsPayload {
        public List<ImageJobSnapshot> jobs = new ArrayList<>();
    }
}
