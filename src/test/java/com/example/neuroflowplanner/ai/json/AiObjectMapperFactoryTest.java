package com.example.neuroflowplanner.ai.json;

import com.example.neuroflowplanner.ai.dto.AiChatResponseDto;
import com.example.neuroflowplanner.ai.dto.ui.AiTaskAutofillResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiObjectMapperFactoryTest {

    @Test
    void failOnUnknownPropertiesWhenEnabled() {
        ObjectMapper mapper = AiObjectMapperFactory.createMapper(true);
        String payload = """
                {
                  "description": "d",
                  "tags": "t",
                  "complexity": 3,
                  "unknownField": "x"
                }
                """;

        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue(payload, AiTaskAutofillResponseDto.class));
    }

    @Test
    void ignoresUnknownPropertiesWhenDisabled() throws Exception {
        ObjectMapper mapper = AiObjectMapperFactory.createMapper(false);
        String payload = """
                {
                  "description": "d",
                  "tags": "t",
                  "complexity": 3,
                  "unknownField": "x"
                }
                """;

        AiTaskAutofillResponseDto dto = mapper.readValue(payload, AiTaskAutofillResponseDto.class);
        assertEquals("d", dto.description());
        assertEquals("t", dto.tags());
        assertEquals(3, dto.complexity());
    }

    @Test
    void failsOnMissingCreatorProperties() {
        ObjectMapper mapper = AiObjectMapperFactory.createMapper(false);
        String payload = """
                {
                  "description": "d",
                  "tags": "t"
                }
                """;

        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue(payload, AiTaskAutofillResponseDto.class));
    }

    @Test
    void failsOnNullPrimitive() {
        ObjectMapper mapper = AiObjectMapperFactory.createMapper(false);
        String payload = """
                {
                  "description": "d",
                  "tags": "t",
                  "complexity": null
                }
                """;

        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue(payload, AiTaskAutofillResponseDto.class));
    }

    @Test
    void failsOnTrailingTokens() {
        ObjectMapper mapper = AiObjectMapperFactory.createMapper(false);
        String payload = """
                {"description":"d","tags":"t","complexity":3}
                {"description":"d2","tags":"t2","complexity":4}
                """;

        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue(payload, AiTaskAutofillResponseDto.class));
    }

    @Test
    void providerMapperAllowsOptionalCreatorPropertiesToBeMissing() throws Exception {
        AiObjectMapperFactory.reloadFromConfig();
        ObjectMapper providerMapper = AiObjectMapperFactory.providerResponseMapper();
        String payload = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "ok"
                      }
                    }
                  ]
                }
                """;

        AiChatResponseDto dto = providerMapper.readValue(payload, AiChatResponseDto.class);
        assertEquals("ok", dto.choices().getFirst().message().content());
    }
}
