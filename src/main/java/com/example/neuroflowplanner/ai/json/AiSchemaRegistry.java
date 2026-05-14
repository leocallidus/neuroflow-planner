package com.example.neuroflowplanner.ai.json;

import com.example.neuroflowplanner.util.ConfigManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AiSchemaRegistry {
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String RESOURCE_PREFIX = "resource:";
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDialect(Dialects.getDraft202012());
    private static final ConcurrentMap<String, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();

    private AiSchemaRegistry() {
    }

    public static Schema getSchema(String classpathSchema) {
        String resourcePath = normalizeSchemaResourcePath(classpathSchema);
        return SCHEMA_CACHE.computeIfAbsent(resourcePath, key -> {
            String schemaData = readSchemaResource(key);
            String schemaLocation = buildSchemaLocation(key);
            Schema schema = REGISTRY.getSchema(SchemaLocation.of(schemaLocation), schemaData, InputFormat.JSON);
            schema.initializeValidators();
            return schema;
        });
    }

    public static AiSchemaValidationResult validateJson(String classpathSchema, String jsonPayload) {
        if (!ConfigManager.isAiJsonSchemaValidationEnabled()
                || ConfigManager.getAiJsonParserMode() == AiJsonParserMode.LEGACY) {
            return AiSchemaValidationResult.ok();
        }
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return AiSchemaValidationResult.invalid(List.of("JSON payload is empty."));
        }
        try {
            Schema schema = getSchema(classpathSchema);
            List<com.networknt.schema.Error> errors = schema.validate(jsonPayload, InputFormat.JSON);
            if (errors == null || errors.isEmpty()) {
                return AiSchemaValidationResult.ok();
            }
            List<String> messages = new ArrayList<>(errors.size());
            for (com.networknt.schema.Error error : errors) {
                messages.add(error.toString());
            }
            return AiSchemaValidationResult.invalid(messages);
        } catch (Exception e) {
            return AiSchemaValidationResult.invalid(List.of("Schema validation failed: " + e.getMessage()));
        }
    }

    public static AiSchemaValidationResult validateNode(String classpathSchema, JsonNode node) {
        if (node == null) {
            return AiSchemaValidationResult.invalid(List.of("JSON payload is null."));
        }
        return validateJson(classpathSchema, node.toString());
    }

    public static void clearCache() {
        SCHEMA_CACHE.clear();
    }

    private static String normalizeSchemaResourcePath(String classpathSchema) {
        if (classpathSchema == null || classpathSchema.isBlank()) {
            throw new IllegalArgumentException("Schema path must not be blank.");
        }
        String normalized = classpathSchema.trim();
        if (normalized.startsWith(CLASSPATH_PREFIX)) {
            normalized = normalized.substring(CLASSPATH_PREFIX.length());
        } else if (normalized.startsWith(RESOURCE_PREFIX)) {
            normalized = normalized.substring(RESOURCE_PREFIX.length());
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Schema path must not be blank.");
        }
        return normalized;
    }

    private static String readSchemaResource(String resourcePath) {
        try (InputStream in = openResource(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Schema resource not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read schema resource: " + resourcePath, e);
        }
    }

    private static InputStream openResource(String resourcePath) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream stream = contextClassLoader.getResourceAsStream(resourcePath);
            if (stream != null) {
                return stream;
            }
        }

        ClassLoader ownClassLoader = AiSchemaRegistry.class.getClassLoader();
        if (ownClassLoader != null) {
            InputStream stream = ownClassLoader.getResourceAsStream(resourcePath);
            if (stream != null) {
                return stream;
            }
        }

        return AiSchemaRegistry.class.getResourceAsStream("/" + resourcePath);
    }

    private static String buildSchemaLocation(String resourcePath) {
        return "https://neuroflowplanner.local/" + resourcePath;
    }
}
