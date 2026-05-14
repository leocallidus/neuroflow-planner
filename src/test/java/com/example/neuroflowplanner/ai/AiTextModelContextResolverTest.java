package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiTextModelContextResolverTest {

    @Test
    void resolvesContextWindowMetadataForSelectedModel() {
        List<AiDiscoveredModelInfo> previous = ConfigManager.getExternalApiModelCatalog();
        try {
            ConfigManager.setExternalApiModelCatalog(List.of(
                    new AiDiscoveredModelInfo(
                            "openai/gpt-5.4",
                            "chat",
                            true,
                            true,
                            false,
                            true,
                            AiTextModelContextMetadata.fromTokens(1_050_000),
                            null)
            ));

            AiTextModelContextMetadata metadata = AiTextModelContextResolver.resolveForModel("openai/gpt-5.4");

            assertEquals(1_050_000, metadata.contextWindowTokens());
            assertEquals("1.05M", metadata.contextWindowLabel());
        } finally {
            ConfigManager.setExternalApiModelCatalog(previous);
        }
    }

    @Test
    void returnsNullWhenModelContextMetadataIsMissing() {
        List<AiDiscoveredModelInfo> previous = ConfigManager.getExternalApiModelCatalog();
        try {
            ConfigManager.setExternalApiModelCatalog(List.of());

            assertNull(AiTextModelContextResolver.resolveForModel("openai/gpt-5.4"));
        } finally {
            ConfigManager.setExternalApiModelCatalog(previous);
        }
    }
}
