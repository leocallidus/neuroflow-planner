package com.example.neuroflowplanner.ai.json;

import com.example.neuroflowplanner.ai.dto.AiChatChoiceDto;
import com.example.neuroflowplanner.ai.dto.AiChatMessageDto;
import com.example.neuroflowplanner.ai.dto.AiChatResponseDto;
import com.example.neuroflowplanner.ai.dto.AiImageDataDto;
import com.example.neuroflowplanner.ai.dto.AiImageResponseDto;
import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.AiModelDescriptorDto;
import com.example.neuroflowplanner.ai.dto.AiModelTopProviderDto;
import com.example.neuroflowplanner.ai.dto.AiModelsResponseDto;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.ai.dto.AiTextModelParameterMetadata;
import com.example.neuroflowplanner.ai.dto.ui.AiTaskAutofillResponseDto;
import com.example.neuroflowplanner.ai.media.AiModelInputCapabilities;
import com.example.neuroflowplanner.util.AiApiUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AiCoreResponseMapper {
    private static final String CHAT_RESPONSE_SCHEMA = "ai/schema/chat-response.schema.json";
    private static final String MODELS_RESPONSE_SCHEMA = "ai/schema/models-response.schema.json";
    private static final String IMAGE_GENERATION_SCHEMA = "ai/schema/image-generation-response.schema.json";
    private static final String IMAGE_POLLING_SCHEMA = "ai/schema/image-polling-response.schema.json";
    private static final String UI_TASK_AUTOFILL_SCHEMA = "ai/schema/ui-task-autofill-response.schema.json";

    private AiCoreResponseMapper() {
    }

    public static String extractChatContent(String responseBody) {
        AiChatResponseDto dto = parseProviderDto(responseBody, CHAT_RESPONSE_SCHEMA, AiChatResponseDto.class);
        String content = projectChatContent(dto);
        if (content == null || content.isBlank()) {
            throw new AiParsingException("AI response does not contain assistant content.");
        }
        return AiApiUtils.sanitizeAssistantText(content);
    }

    public static List<String> extractModelNames(String responseBody) {
        List<AiDiscoveredModelInfo> catalog = extractModelCatalog(responseBody);
        List<String> names = new ArrayList<>(catalog.size());
        for (AiDiscoveredModelInfo model : catalog) {
            if (model != null && model.id() != null && !model.id().isBlank()) {
                names.add(model.id());
            }
        }
        return names;
    }

    public static List<AiDiscoveredModelInfo> extractModelCatalog(String responseBody) {
        AiModelsResponseDto dto = parseProviderDto(responseBody, MODELS_RESPONSE_SCHEMA, AiModelsResponseDto.class);
        Map<String, AiDiscoveredModelInfo> catalog = new LinkedHashMap<>();
        appendModelCatalog(dto.data(), catalog);
        appendModelCatalog(dto.models(), catalog);

        if (catalog.isEmpty()) {
            throw new AiParsingException("Models response does not contain model identifiers.");
        }
        return List.copyOf(catalog.values());
    }

    public static AiImageResponseDto parseImageGenerationResponse(String responseBody) {
        return parseProviderDto(responseBody, IMAGE_GENERATION_SCHEMA, AiImageResponseDto.class);
    }

    public static AiImageResponseDto parseImagePollingResponse(String responseBody) {
        return parseProviderDto(responseBody, IMAGE_POLLING_SCHEMA, AiImageResponseDto.class);
    }

    public static String extractImageUrlFromGeneration(String responseBody) {
        AiImageResponseDto dto = parseImageGenerationResponse(responseBody);
        return projectBestImageUrl(dto);
    }

    public static String extractImageRequestIdFromGeneration(String responseBody) {
        AiImageResponseDto dto = parseImageGenerationResponse(responseBody);
        String requestId = firstNonBlank(dto.requestId(), dto.id());
        if (requestId == null) {
            throw new AiParsingException("Image generation response does not contain requestId.");
        }
        return requestId;
    }

    public static String extractImageStatusOrStateFromPolling(String responseBody) {
        AiImageResponseDto dto = parseImagePollingResponse(responseBody);
        return firstNonBlank(dto.status(), dto.state());
    }

    public static String extractImageUrlFromPolling(String responseBody) {
        AiImageResponseDto dto = parseImagePollingResponse(responseBody);
        return projectBestImageUrl(dto);
    }

    public static String extractImageStatusFromHistory(String responseBody) {
        JsonNode root = parseJsonNode(responseBody, false);
        return firstNonBlank(root.path("status").asText(null), root.path("state").asText(null));
    }

    public static String extractImageUrlFromHistory(String responseBody) {
        JsonNode root = parseJsonNode(responseBody, false);
        String direct = firstHttpUrl(
                root.path("resultUrl").asText(null),
                root.path("imageUrl").asText(null),
                root.path("outputUrl").asText(null),
                root.path("url").asText(null));
        if (direct != null) {
            return direct;
        }
        return findHttpUrlInNode(root, null);
    }

    public static AiTaskAutofillResponseDto parseUiTaskAutofillResponse(String responseBody) {
        String cleanedPayload = stripMarkdownJsonFences(responseBody);
        JsonNode root = parseJsonNode(cleanedPayload, true);
        if (root.isTextual()) {
            root = parseJsonNode(root.textValue(), true);
        }
        root = normalizeUiAutofillPayload(root);
        validateSchema(root, UI_TASK_AUTOFILL_SCHEMA);
        AiTaskAutofillResponseDto dto = toDto(root, AiTaskAutofillResponseDto.class, true);
        return validateUiAutofillDto(dto);
    }

    public static String projectBestImageUrl(AiImageResponseDto dto) {
        if (dto == null) {
            return null;
        }

        String url = firstHttpUrl(dto.resultUrl(), dto.imageUrl(), dto.outputUrl(), dto.url());
        if (url != null) {
            return url;
        }

        JsonNode data = dto.data();
        if (data == null || data.isNull()) {
            return null;
        }
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (item == null || item.isNull()) {
                    continue;
                }
                String candidate = firstHttpUrl(item.path("url").asText(null));
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (data.isObject()) {
            String candidate = firstHttpUrl(data.path("url").asText(null));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String findHttpUrlInNode(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText(null);
            if (text == null || text.isBlank()) {
                return null;
            }
            if (fieldName == null || looksLikeUrlCarrierField(fieldName)) {
                return firstHttpUrl(text);
            }
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String candidate = findHttpUrlInNode(item, fieldName);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }

        String preferred = findHttpUrlInPreferredFields(node);
        if (preferred != null) {
            return preferred;
        }

        var fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String candidate = findHttpUrlInNode(entry.getValue(), entry.getKey());
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String findHttpUrlInPreferredFields(JsonNode node) {
        for (String field : List.of("resultUrl", "imageUrl", "outputUrl", "url", "downloadUrl", "fileUrl")) {
            JsonNode child = node.get(field);
            String candidate = findHttpUrlInNode(child, field);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean looksLikeUrlCarrierField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("url") || normalized.contains("file") || normalized.contains("image");
    }

    private static <T> T parseProviderDto(String responseBody, String schemaPath, Class<T> dtoType) {
        JsonNode root = parseJsonNode(responseBody, false);
        validateSchema(root, schemaPath);
        return toDto(root, dtoType, false);
    }

    private static JsonNode normalizeUiAutofillPayload(JsonNode root) {
        if (!(root instanceof ObjectNode objectNode)) {
            return root;
        }

        JsonNode complexity = objectNode.get("complexity");
        if (complexity != null && complexity.isTextual()) {
            Integer normalized = extractLeadingInteger(complexity.asText());
            if (normalized != null) {
                objectNode.put("complexity", normalized);
            }
        }
        return objectNode;
    }

    private static JsonNode parseJsonNode(String responseBody, boolean strictUiMapper) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new AiParsingException("AI response is empty.");
        }
        ObjectMapper mapper = selectMapper(strictUiMapper);
        try {
            return mapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new AiParsingException("Malformed JSON in AI response.", e);
        }
    }

    private static <T> T toDto(JsonNode root, Class<T> dtoType, boolean strictUiMapper) {
        ObjectMapper mapper = selectMapper(strictUiMapper);
        try {
            return mapper.treeToValue(root, dtoType);
        } catch (JsonProcessingException e) {
            throw new AiParsingException("Failed to map AI response to DTO " + dtoType.getSimpleName() + ".", e);
        }
    }

    private static ObjectMapper selectMapper(boolean strictUiMapper) {
        if (strictUiMapper) {
            return AiObjectMapperFactory.strictUiMapper();
        }
        return AiObjectMapperFactory.providerResponseMapper();
    }

    private static void validateSchema(JsonNode root, String schemaPath) {
        AiSchemaValidationResult validation = AiSchemaRegistry.validateNode(schemaPath, root);
        if (!validation.valid()) {
            throw new AiSchemaValidationException(schemaPath, validation.messages());
        }
    }

    private static void appendModelCatalog(List<AiModelDescriptorDto> descriptors, Map<String, AiDiscoveredModelInfo> out) {
        if (descriptors == null || descriptors.isEmpty()) {
            return;
        }
        for (AiModelDescriptorDto descriptor : descriptors) {
            if (descriptor == null) {
                continue;
            }
            String candidate = firstNonBlank(descriptor.id(), descriptor.name());
            if (candidate != null) {
                AiModelInputCapabilities capabilities =
                        AiModelInputCapabilities.fromInputModalities(mergeModalities(
                                descriptor.inputModalities(),
                                descriptor.architecture() == null ? null : descriptor.architecture().inputModalities()));
                AiTextModelContextMetadata textContextMetadata = extractTextContextMetadata(descriptor);
                AiTextModelParameterMetadata textParameterMetadata = extractTextParameterMetadata(descriptor);
                AiDiscoveredModelInfo incoming = new AiDiscoveredModelInfo(
                        candidate,
                        normalizeModelType(descriptor.type()),
                        isMultimodal(descriptor, capabilities),
                        capabilities.supportsImageInput(),
                        capabilities.supportsAudioInput(),
                        capabilities.supportsFileInput(),
                        textContextMetadata,
                        textParameterMetadata);
                AiDiscoveredModelInfo existing = out.get(candidate);
                if (existing == null) {
                    out.put(candidate, incoming);
                } else {
                    out.put(candidate, new AiDiscoveredModelInfo(
                            candidate,
                            firstNonBlank(existing.type(), incoming.type()),
                            existing.multimodal() || incoming.multimodal(),
                            existing.supportsImageInput() || incoming.supportsImageInput(),
                            existing.supportsAudioInput() || incoming.supportsAudioInput(),
                            existing.supportsFileInput() || incoming.supportsFileInput(),
                            mergeTextContextMetadata(existing.textContextMetadata(), incoming.textContextMetadata()),
                            mergeTextParameterMetadata(existing.textParameterMetadata(), incoming.textParameterMetadata())));
                }
            }
        }
    }

    private static AiTextModelContextMetadata extractTextContextMetadata(AiModelDescriptorDto descriptor) {
        if (descriptor == null || !"chat".equalsIgnoreCase(normalizeModelType(descriptor.type()))) {
            return null;
        }
        AiModelTopProviderDto topProvider = descriptor.topProvider();
        if (topProvider == null || topProvider.contextLength() == null || topProvider.contextLength() <= 0) {
            return null;
        }
        return AiTextModelContextMetadata.fromTokens(topProvider.contextLength());
    }

    private static AiTextModelParameterMetadata extractTextParameterMetadata(AiModelDescriptorDto descriptor) {
        if (descriptor == null || !"chat".equalsIgnoreCase(normalizeModelType(descriptor.type()))) {
            return null;
        }
        AiModelTopProviderDto topProvider = descriptor.topProvider();
        if (topProvider == null) {
            return null;
        }
        List<String> supportedParameters = topProvider.supportedParameters();
        boolean supportsTemperature = containsSupportedParameter(supportedParameters, "temperature");
        boolean supportsTopP = containsSupportedParameter(supportedParameters, "top_p");
        boolean supportsFrequencyPenalty = containsSupportedParameter(supportedParameters, "frequency_penalty");
        boolean supportsPresencePenalty = containsSupportedParameter(supportedParameters, "presence_penalty");
        Integer maxCompletionTokens = topProvider.maxCompletionTokens();
        Double defaultTemperature = topProvider.defaultParameters() == null ? null : topProvider.defaultParameters().temperature();
        Double defaultTopP = topProvider.defaultParameters() == null ? null : topProvider.defaultParameters().topP();
        Double defaultFrequencyPenalty = topProvider.defaultParameters() == null ? null : topProvider.defaultParameters().frequencyPenalty();
        Double defaultPresencePenalty = topProvider.defaultParameters() == null ? null : topProvider.defaultParameters().presencePenalty();
        if (maxCompletionTokens == null
                && !supportsTemperature
                && !supportsTopP
                && !supportsFrequencyPenalty
                && !supportsPresencePenalty
                && defaultTemperature == null
                && defaultTopP == null
                && defaultFrequencyPenalty == null
                && defaultPresencePenalty == null) {
            return null;
        }
        return new AiTextModelParameterMetadata(
                maxCompletionTokens,
                supportsTemperature,
                supportsTopP,
                supportsFrequencyPenalty,
                supportsPresencePenalty,
                defaultTemperature,
                defaultTopP,
                defaultFrequencyPenalty,
                defaultPresencePenalty);
    }

    private static boolean containsSupportedParameter(List<String> supportedParameters, String key) {
        if (supportedParameters == null || supportedParameters.isEmpty() || key == null || key.isBlank()) {
            return false;
        }
        for (String supportedParameter : supportedParameters) {
            if (supportedParameter != null && supportedParameter.trim().equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private static AiTextModelParameterMetadata mergeTextParameterMetadata(
            AiTextModelParameterMetadata existing,
            AiTextModelParameterMetadata incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null) {
            return existing;
        }
        return new AiTextModelParameterMetadata(
                firstNonNull(existing.maxCompletionTokens(), incoming.maxCompletionTokens()),
                existing.supportsTemperature() || incoming.supportsTemperature(),
                existing.supportsTopP() || incoming.supportsTopP(),
                existing.supportsFrequencyPenalty() || incoming.supportsFrequencyPenalty(),
                existing.supportsPresencePenalty() || incoming.supportsPresencePenalty(),
                firstNonNull(existing.defaultTemperature(), incoming.defaultTemperature()),
                firstNonNull(existing.defaultTopP(), incoming.defaultTopP()),
                firstNonNull(existing.defaultFrequencyPenalty(), incoming.defaultFrequencyPenalty()),
                firstNonNull(existing.defaultPresencePenalty(), incoming.defaultPresencePenalty()));
    }

    private static AiTextModelContextMetadata mergeTextContextMetadata(
            AiTextModelContextMetadata existing,
            AiTextModelContextMetadata incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null) {
            return existing;
        }
        return new AiTextModelContextMetadata(
                firstNonNull(existing.contextWindowTokens(), incoming.contextWindowTokens()),
                firstNonNull(existing.contextWindowLabel(), incoming.contextWindowLabel()));
    }

    private static String normalizeModelType(String value) {
        String normalized = firstNonBlank(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean isMultimodal(AiModelDescriptorDto descriptor, AiModelInputCapabilities capabilities) {
        if (descriptor == null) {
            return false;
        }
        if (capabilities != null && capabilities.supportsAnyMediaInput()) {
            return true;
        }
        String modality = descriptor.architecture() == null ? null : descriptor.architecture().modality();
        if (containsNonTextModality(modality)) {
            return true;
        }
        if (containsNonTextModality(mergeModalities(
                descriptor.inputModalities(),
                descriptor.architecture() == null ? null : descriptor.architecture().inputModalities()))) {
            return true;
        }
        return containsNonTextModality(mergeModalities(
                descriptor.outputModalities(),
                descriptor.architecture() == null ? null : descriptor.architecture().outputModalities()));
    }

    private static boolean containsNonTextModality(List<String> modalities) {
        if (modalities == null || modalities.isEmpty()) {
            return false;
        }
        for (String modality : modalities) {
            if (containsNonTextModality(modality)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNonTextModality(String rawValue) {
        String normalized = firstNonBlank(rawValue);
        if (normalized == null) {
            return false;
        }
        String value = normalized.toLowerCase(Locale.ROOT);
        return value.contains("image")
                || value.contains("audio")
                || value.contains("video")
                || value.contains("vision")
                || value.contains("speech");
    }

    private static List<String> mergeModalities(List<String> primary, List<String> secondary) {
        if ((primary == null || primary.isEmpty()) && (secondary == null || secondary.isEmpty())) {
            return List.of();
        }
        List<String> merged = new ArrayList<>();
        appendUniqueModalities(merged, primary);
        appendUniqueModalities(merged, secondary);
        return List.copyOf(merged);
    }

    private static void appendUniqueModalities(List<String> target, List<String> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (String value : source) {
            String normalized = normalizeNonBlank(value);
            if (normalized == null) {
                continue;
            }
            boolean exists = false;
            for (String existing : target) {
                if (existing.equalsIgnoreCase(normalized)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                target.add(normalized);
            }
        }
    }

    private static String projectChatContent(AiChatResponseDto dto) {
        if (dto == null) {
            return null;
        }

        List<AiChatChoiceDto> choices = dto.choices();
        if (choices != null) {
            for (AiChatChoiceDto choice : choices) {
                if (choice == null) {
                    continue;
                }
                AiChatMessageDto message = choice.message();
                String messageContent = message == null ? null : message.content();
                String resolved = firstNonBlank(messageContent);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        String topLevelContent = firstNonBlank(dto.content());
        if (topLevelContent != null) {
            return topLevelContent;
        }

        if (choices != null) {
            for (AiChatChoiceDto choice : choices) {
                if (choice == null) {
                    continue;
                }
                String text = firstNonBlank(choice.text());
                if (text != null) {
                    return text;
                }
            }
        }

        String response = firstNonBlank(dto.response());
        if (response != null) {
            return response;
        }

        AiChatMessageDto message = dto.message();
        return firstNonBlank(message == null ? null : message.content());
    }

    private static AiTaskAutofillResponseDto validateUiAutofillDto(AiTaskAutofillResponseDto dto) {
        if (dto == null) {
            throw new AiParsingException("UI autofill response is empty.");
        }

        String description = normalizeNonBlank(dto.description());
        if (description == null) {
            throw new AiParsingException("UI autofill response does not contain non-empty description.");
        }

        String tags = normalizeNonBlank(dto.tags());
        if (tags == null) {
            throw new AiParsingException("UI autofill response does not contain non-empty tags.");
        }

        int complexity = dto.complexity();
        if (complexity < 1 || complexity > 10) {
            throw new AiParsingException("UI autofill complexity must be in range 1..10.");
        }

        return new AiTaskAutofillResponseDto(description, tags, complexity);
    }

    private static Integer extractLeadingInteger(String value) {
        if (value == null) {
            return null;
        }
        String input = value.trim();
        if (input.isEmpty()) {
            return null;
        }

        StringBuilder digits = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
                started = true;
                continue;
            }
            if (started) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stripMarkdownJsonFences(String payload) {
        if (payload == null) {
            return null;
        }
        String cleaned = payload.trim();
        if (cleaned.contains("```json")) {
            cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        } else if (cleaned.contains("```")) {
            cleaned = cleaned.substring(cleaned.indexOf("```") + 3);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        }
        return cleaned.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeNonBlank(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static <T> T firstNonNull(T primary, T secondary) {
        return primary != null ? primary : secondary;
    }

    private static String normalizeNonBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private static String firstHttpUrl(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            String normalized = normalizeNonBlank(candidate);
            if (normalized == null) {
                continue;
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                return normalized;
            }
        }
        return null;
    }
}
