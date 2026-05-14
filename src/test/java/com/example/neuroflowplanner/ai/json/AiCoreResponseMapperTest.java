package com.example.neuroflowplanner.ai.json;

import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.ui.AiTaskAutofillResponseDto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiCoreResponseMapperTest {

    @Test
    void parsesUiAutofillStrictPayload() {
        String payload = readFixture("ai/payloads/ui/autofill/happy_strict_contract.json");

        AiTaskAutofillResponseDto dto = AiCoreResponseMapper.parseUiTaskAutofillResponse(payload);

        assertEquals("Prepare sprint plan and publish board updates.", dto.description());
        assertEquals("planning,team", dto.tags());
        assertEquals(5, dto.complexity());
    }

    @Test
    void parsesUiAutofillMarkdownWrappedPayload() {
        String payload = readFixture("ai/payloads/ui/autofill/edge_markdown_fenced_json.txt");

        AiTaskAutofillResponseDto dto = AiCoreResponseMapper.parseUiTaskAutofillResponse(payload);

        assertEquals("Write release notes.", dto.description());
        assertEquals("docs,release", dto.tags());
        assertEquals(4, dto.complexity());
    }

    @Test
    void normalizesUiAutofillComplexityFromString() {
        String payload = readFixture("ai/payloads/ui/autofill/edge_complexity_string.json");

        AiTaskAutofillResponseDto dto = AiCoreResponseMapper.parseUiTaskAutofillResponse(payload);

        assertEquals("Refine API error handling.", dto.description());
        assertEquals("backend,reliability", dto.tags());
        assertEquals(7, dto.complexity());
    }

    @Test
    void failsOnMissingRequiredUiAutofillFields() {
        String payload = readFixture("ai/payloads/ui/autofill/malformed_missing_required_fields.json");

        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper.parseUiTaskAutofillResponse(payload));
    }

    @Test
    void failsOnOutOfRangeUiAutofillComplexity() {
        String payload = readFixture("ai/payloads/ui/autofill/malformed_complexity_out_of_range.json");

        assertThrows(AiParsingException.class, () -> AiCoreResponseMapper.parseUiTaskAutofillResponse(payload));
    }

    @Test
    void extractsModelInputCapabilitiesFromInputModalities() {
        String payload = """
            {
              "data": [
                {
                  "id": "openai/gpt-4o",
                  "type": "chat",
                  "input_modalities": ["text", "image", "audio", "application/pdf"]
                }
              ]
            }
            """;

        List<AiDiscoveredModelInfo> catalog = AiCoreResponseMapper.extractModelCatalog(payload);

        assertEquals(1, catalog.size());
        AiDiscoveredModelInfo model = catalog.getFirst();
        assertTrue(model.multimodal());
        assertTrue(model.supportsImageInput());
        assertTrue(model.supportsAudioInput());
        assertTrue(model.supportsFileInput());
    }

    @Test
    void extractsModelInputCapabilitiesFromNestedArchitectureModalities() {
        String payload = """
            {
              "data": [
                {
                  "id": "openai/gpt-5.4",
                  "type": "chat",
                  "architecture": {
                    "modality": "text",
                    "input_modalities": ["text", "image", "document", "application/pdf"],
                    "output_modalities": ["text"]
                  },
                  "top_provider": {
                    "context_length": 1050000,
                    "max_completion_tokens": 8192,
                    "supported_parameters": ["temperature", "top_p", "frequency_penalty", "presence_penalty"],
                    "default_parameters": {
                      "temperature": 1.0,
                      "top_p": 0.95,
                      "frequency_penalty": 0.1,
                      "presence_penalty": 0.2
                    }
                  }
                }
              ]
            }
            """;

        List<AiDiscoveredModelInfo> catalog = AiCoreResponseMapper.extractModelCatalog(payload);

        assertEquals(1, catalog.size());
        AiDiscoveredModelInfo model = catalog.getFirst();
        assertTrue(model.multimodal());
        assertTrue(model.supportsImageInput());
        assertTrue(model.supportsFileInput());
        assertEquals(1_050_000, model.textContextMetadata().contextWindowTokens());
        assertEquals("1.05M", model.textContextMetadata().contextWindowLabel());
        assertEquals(8192, model.textParameterMetadata().maxCompletionTokens());
        assertTrue(model.textParameterMetadata().supportsTemperature());
        assertTrue(model.textParameterMetadata().supportsTopP());
        assertTrue(model.textParameterMetadata().supportsFrequencyPenalty());
        assertTrue(model.textParameterMetadata().supportsPresencePenalty());
    }

    private String readFixture(String classpathLocation) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found: " + classpathLocation);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fixture: " + classpathLocation, e);
        }
    }
}
