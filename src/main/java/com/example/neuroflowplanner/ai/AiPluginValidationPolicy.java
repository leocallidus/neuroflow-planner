package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.util.AiConfigDefaults;

public final class AiPluginValidationPolicy {

    private AiPluginValidationPolicy() {
    }

    public static void validate(AiRequestOptions.PluginOptions pluginOptions) {
        if (pluginOptions == null) {
            return;
        }
        validateWeb(pluginOptions.web());
        validateFileParser(pluginOptions.fileParser());
        validateResponseHealing(pluginOptions.responseHealing());
    }

    private static void validateWeb(AiRequestOptions.WebPluginOptions web) {
        if (web == null || !web.enabled()) {
            return;
        }
        String engine = web.engine();
        if (engine != null
                && !engine.isBlank()
                && !AiConfigDefaults.PLUGIN_WEB_ENGINE_OPTIONS.contains(engine.trim().toLowerCase())) {
            throw new AiPluginValidationException(
                    "Плагин web: недопустимый engine. Разрешены только auto, native или exa.");
        }
        Integer maxResults = web.maxResults();
        if (maxResults != null && (maxResults < 1 || maxResults > 20)) {
            throw new AiPluginValidationException(
                    "Плагин web: max results должен быть в диапазоне 1..20.");
        }
    }

    private static void validateFileParser(AiRequestOptions.FileParserPluginOptions fileParser) {
        if (fileParser == null || !fileParser.enabled()) {
            return;
        }
        String pdfEngine = fileParser.pdfEngine();
        if (pdfEngine != null
                && !pdfEngine.isBlank()
                && !AiConfigDefaults.PLUGIN_FILE_PARSER_PDF_ENGINE_OPTIONS.contains(pdfEngine.trim().toLowerCase())) {
            throw new AiPluginValidationException(
                    "Плагин file-parser: недопустимый PDF engine. Разрешены только pdf-text, mistral-ocr или native.");
        }
    }

    private static void validateResponseHealing(AiRequestOptions.ResponseHealingPluginOptions responseHealing) {
        if (responseHealing == null) {
            return;
        }
        // No extra fields to validate at the moment.
    }
}
