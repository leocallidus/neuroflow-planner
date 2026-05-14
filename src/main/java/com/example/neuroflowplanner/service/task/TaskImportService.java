package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;

import java.util.List;

public interface TaskImportService {

    enum ImportFormat {
        JSON,
        CSV
    }

    enum DuplicateIdPolicy {
        KEEP_FIRST,
        KEEP_LAST
    }

    enum TitleDedupePolicy {
        ALLOW_DUPLICATES,
        SKIP_EXISTING
    }

    record ImportOptions(
        DuplicateIdPolicy duplicateIdPolicy,
        TitleDedupePolicy titleDedupePolicy,
        boolean recalculatePriority
    ) {
        public ImportOptions {
            if (duplicateIdPolicy == null) {
                duplicateIdPolicy = DuplicateIdPolicy.KEEP_LAST;
            }
            if (titleDedupePolicy == null) {
                titleDedupePolicy = TitleDedupePolicy.SKIP_EXISTING;
            }
        }

        public static ImportOptions defaults() {
            return new ImportOptions(DuplicateIdPolicy.KEEP_LAST, TitleDedupePolicy.SKIP_EXISTING, true);
        }
    }

    record ImportPreview(
        ImportFormat format,
        int sourceCount,
        int acceptedCount,
        int toCreateCount,
        int toUpdateCount,
        int duplicateIdCount,
        int duplicateTitleCount,
        int invalidCount,
        List<String> warnings,
        List<Task> tasksToPersist
    ) {
        public ImportPreview {
            if (format == null) {
                throw new IllegalArgumentException("Import format is required");
            }
            if (sourceCount < 0 || acceptedCount < 0 || toCreateCount < 0 || toUpdateCount < 0
                || duplicateIdCount < 0 || duplicateTitleCount < 0 || invalidCount < 0) {
                throw new IllegalArgumentException("Import preview counters must be >= 0");
            }
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            tasksToPersist = tasksToPersist == null ? List.of() : List.copyOf(tasksToPersist);
        }

        public boolean hasChanges() {
            return !tasksToPersist.isEmpty();
        }
    }

    record ImportResult(
        ImportPreview preview,
        TaskBulkOperationResult bulkResult
    ) {
        public ImportResult {
            if (preview == null) {
                throw new IllegalArgumentException("Import preview is required");
            }
            if (bulkResult == null) {
                throw new IllegalArgumentException("Bulk result is required");
            }
        }
    }

    ImportPreview dryRun(String payload, ImportFormat format, ImportOptions options);

    default ImportResult apply(String payload, ImportFormat format, ImportOptions options) {
        return apply(dryRun(payload, format, options));
    }

    ImportResult apply(ImportPreview preview);
}
