package com.example.neuroflowplanner.ai.media;

import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.ConfigManager;

import java.util.List;

public final class AiModelMediaCapabilityPolicy {

    private AiModelMediaCapabilityPolicy() {
    }

    public static boolean supportsImageInput(String modelId) {
        String normalized = AiConfigDefaults.normalizeExternalModelId(modelId);
        return !normalized.isBlank() && ConfigManager.getExternalApiMultimodalModels().contains(normalized);
    }

    public static boolean supportsAudioInput(String modelId) {
        String normalized = AiConfigDefaults.normalizeExternalModelId(modelId);
        return !normalized.isBlank() && ConfigManager.getExternalApiAudioInputModels().contains(normalized);
    }

    public static boolean supportsFileInput(String modelId) {
        String normalized = AiConfigDefaults.normalizeExternalModelId(modelId);
        return !normalized.isBlank()
                && (ConfigManager.getExternalApiFileInputModels().contains(normalized)
                || supportsKnownPolzaDocumentInput(normalized));
    }

    public static void validateExternalModelMediaInputs(String modelId, List<AiMediaInput> mediaInputs) {
        String normalizedModel = AiConfigDefaults.normalizeExternalModelId(modelId);
        if (normalizedModel.isBlank() || mediaInputs == null || mediaInputs.isEmpty()) {
            return;
        }

        boolean hasVideo = false;
        boolean requiresImage = false;
        boolean requiresAudio = mediaInputs.stream()
                .filter(input -> input != null)
                .anyMatch(input -> input.kind() == AiMediaInputKind.AUDIO);
        boolean requiresFile = mediaInputs.stream()
                .filter(input -> input != null)
                .anyMatch(input -> input.kind() == AiMediaInputKind.DOCUMENT);
        boolean hasOtherThanAudio = false;

        for (AiMediaInput input : mediaInputs) {
            if (input == null) {
                continue;
            }
            if (input.kind() == AiMediaInputKind.VIDEO) {
                hasVideo = true;
            }
            if (input.kind() == AiMediaInputKind.IMAGE) {
                requiresImage = true;
                hasOtherThanAudio = true;
            }
            if (input.kind() == AiMediaInputKind.DOCUMENT) {
                hasOtherThanAudio = true;
            }
        }

        if (hasVideo) {
            throw new AiMediaCapabilityValidationException("Видео на вход пока не поддерживается.");
        }
        if (requiresAudio && hasOtherThanAudio) {
            throw new AiMediaCapabilityValidationException(
                    "Аудио пока можно отправлять только отдельно, без изображений и документов.");
        }
        if (requiresImage && !supportsImageInput(normalizedModel)) {
            throw new AiMediaCapabilityValidationException(
                    "Модель '" + normalizedModel + "' не поддерживает изображения на вход.");
        }

        if (requiresAudio && !supportsAudioInput(normalizedModel)) {
            throw new AiMediaCapabilityValidationException(
                    "Модель '" + normalizedModel + "' не поддерживает аудио на вход.");
        }
        if (requiresFile && !supportsFileInput(normalizedModel)) {
            throw new AiMediaCapabilityValidationException(
                    "Модель '" + normalizedModel + "' не поддерживает файлы на вход.");
        }
    }

    private static boolean supportsKnownPolzaDocumentInput(String normalizedModel) {
        String value = normalizedModel == null ? "" : normalizedModel.trim().toLowerCase();
        return value.startsWith("openai/gpt-5.")
                || value.startsWith("google/gemini-3");
    }
}
