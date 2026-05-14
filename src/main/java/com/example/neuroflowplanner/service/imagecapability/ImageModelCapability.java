package com.example.neuroflowplanner.service.imagecapability;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Capability description for a single image generation model.
 */
public final class ImageModelCapability {

    private final String model;
    private final Map<ImageCapabilityField, FieldCapability> fieldCapabilities;
    private final Map<String, String> fixedPayloadFields;
    private final List<String> documentedParameters;

    ImageModelCapability(
        String model,
        Map<ImageCapabilityField, FieldCapability> fieldCapabilities,
        Map<String, String> fixedPayloadFields,
        List<String> documentedParameters
    ) {
        this.model = model;
        this.fieldCapabilities = Map.copyOf(fieldCapabilities);
        this.fixedPayloadFields = Map.copyOf(fixedPayloadFields);
        this.documentedParameters = documentedParameters == null ? List.of() : List.copyOf(documentedParameters);
    }

    public String model() {
        return model;
    }

    public boolean supports(ImageCapabilityField field) {
        return fieldCapabilities.containsKey(field);
    }

    public List<String> supportedValues(ImageCapabilityField field) {
        FieldCapability capability = fieldCapabilities.get(field);
        return capability == null ? List.of() : capability.allowedValues();
    }

    public String defaultValue(ImageCapabilityField field) {
        FieldCapability capability = fieldCapabilities.get(field);
        return capability == null ? "" : capability.defaultValue();
    }

    public String transportFieldName(ImageCapabilityField field) {
        FieldCapability capability = fieldCapabilities.get(field);
        return capability == null ? "" : capability.transportFieldName();
    }

    public Map<String, String> fixedPayloadFields() {
        return fixedPayloadFields;
    }

    public List<String> documentedParameters() {
        return documentedParameters;
    }

    public String primaryFieldLabel() {
        if (supports(ImageCapabilityField.ASPECT_RATIO)) {
            return ImageCapabilityField.ASPECT_RATIO.displayName();
        }
        if (supports(ImageCapabilityField.SIZE)) {
            return ImageCapabilityField.SIZE.displayName();
        }
        return "Параметр модели";
    }

    public List<String> primaryFieldOptions() {
        if (supports(ImageCapabilityField.ASPECT_RATIO)) {
            return supportedValues(ImageCapabilityField.ASPECT_RATIO);
        }
        if (supports(ImageCapabilityField.SIZE)) {
            return supportedValues(ImageCapabilityField.SIZE);
        }
        return List.of();
    }

    public String normalizeValue(ImageCapabilityField field, String value) {
        FieldCapability capability = fieldCapabilities.get(field);
        String normalized = value == null ? "" : value.trim();
        if (capability == null) {
            if (normalized.isEmpty()) {
                return "";
            }
            throw new ImageCapabilityValidationException(
                "Модель '" + model + "' не поддерживает параметр '" + field.key() + "'."
            );
        }
        if (normalized.isEmpty()) {
            return capability.defaultValue();
        }
        if (capability.allowedValues().isEmpty()) {
            return normalized;
        }
        if (capability.allowedValues().contains(normalized)) {
            return normalized;
        }
        throw new ImageCapabilityValidationException(
            "Недопустимое значение '" + normalized + "' для параметра '" + field.key()
                + "' модели '" + model + "'. Допустимо: " + String.join(", ", capability.allowedValues()) + "."
        );
    }

    public static Builder builder(String model) {
        return new Builder(model);
    }

    public static final class Builder {
        private final String model;
        private final Map<ImageCapabilityField, FieldCapability> fields = new LinkedHashMap<>();
        private final Map<String, String> fixedPayloadFields = new LinkedHashMap<>();
        private final LinkedHashSet<String> documentedParameters = new LinkedHashSet<>();

        private Builder(String model) {
            this.model = model;
        }

        public Builder support(
            ImageCapabilityField field,
            String transportFieldName,
            List<String> allowedValues,
            String defaultValue
        ) {
            fields.put(field, new FieldCapability(
                transportFieldName == null ? field.key() : transportFieldName.trim(),
                allowedValues == null ? List.of() : List.copyOf(allowedValues),
                defaultValue == null ? "" : defaultValue.trim()
            ));
            return this;
        }

        public Builder fixedPayloadField(String key, String value) {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                fixedPayloadFields.put(key.trim(), value.trim());
            }
            return this;
        }

        public Builder documentedParameters(String... parameters) {
            if (parameters == null) {
                return this;
            }
            for (String parameter : parameters) {
                if (parameter != null && !parameter.isBlank()) {
                    documentedParameters.add(parameter.trim());
                }
            }
            return this;
        }

        public ImageModelCapability build() {
            return new ImageModelCapability(model, fields, fixedPayloadFields, List.copyOf(documentedParameters));
        }
    }

    private record FieldCapability(
        String transportFieldName,
        List<String> allowedValues,
        String defaultValue
    ) {
    }
}
