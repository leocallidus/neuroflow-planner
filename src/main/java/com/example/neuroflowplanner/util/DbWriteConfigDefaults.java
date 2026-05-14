package com.example.neuroflowplanner.util;

public final class DbWriteConfigDefaults {
    public static final String CONFIG_DB_BULK_WRITES_MODE = "db.bulk.writes.mode";
    public static final String CONFIG_DB_BULK_BATCH_SIZE = "db.bulk.batch.size";

    public static final String MODE_LEGACY = "legacy";
    public static final String MODE_TRANSACTIONAL = "transactional";
    public static final String MODE_BATCHED = "batched";

    // Stage-2 default keeps legacy behavior until bulk DAL methods are rolled out.
    public static final String DB_BULK_WRITES_MODE_DEFAULT = MODE_LEGACY;

    // ADR-0009: v1 operational range.
    public static final int DB_BULK_BATCH_SIZE_DEFAULT = 200;
    public static final int DB_BULK_BATCH_SIZE_MIN = 50;
    public static final int DB_BULK_BATCH_SIZE_MAX = 500;

    private DbWriteConfigDefaults() {
    }
}
