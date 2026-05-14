package com.example.neuroflowplanner.ai.json;

public final class AiJsonParserModeResolver {

    private AiJsonParserModeResolver() {
    }

    public static AiJsonParserMode resolve(String rawMode) {
        return AiJsonParserMode.fromConfigValue(rawMode);
    }
}
