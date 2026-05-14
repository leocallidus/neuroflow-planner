package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.ConfigManager;

import java.util.List;

public final class AiTextModelContextResolver {
    private AiTextModelContextResolver() {
    }

    public static AiTextModelContextMetadata resolveForModel(String modelId) {
        String normalizedModelId = AiConfigDefaults.normalizeExternalModelId(modelId);
        if (normalizedModelId.isBlank()) {
            return null;
        }
        List<AiDiscoveredModelInfo> catalog = ConfigManager.getExternalApiModelCatalog();
        for (AiDiscoveredModelInfo info : catalog) {
            if (info == null || info.id() == null) {
                continue;
            }
            if (normalizedModelId.equalsIgnoreCase(AiConfigDefaults.normalizeExternalModelId(info.id()))) {
                return info.textContextMetadata();
            }
        }
        return null;
    }
}
