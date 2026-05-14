# AI Payload Corpus (Stage 1)

This corpus is the baseline for P1 JSON parsing migration.

Expected outcome labels:
- `success`: payload should pass parsing contract.
- `success_with_fallback`: payload should parse via approved fallback branch.
- `error_ai_response_invalid`: payload should map to `AI_RESPONSE_INVALID`.
- `error_validation_required`: payload should map to `VALIDATION_REQUIRED_FIELD`.
- `error_validation_invalid`: payload should map to `VALIDATION_INVALID_VALUE`.
- `error_ai_provider`: payload should map to `AI_PROVIDER_ERROR`.

## OpenAI chat completions
- `openai/chat/happy_message_content.json` -> `success`
- `openai/chat/edge_text_fallback.json` -> `success_with_fallback`
- `openai/chat/edge_response_fallback.json` -> `success_with_fallback`
- `openai/chat/malformed_missing_content.json` -> `error_ai_response_invalid`
- `openai/chat/malformed_truncated.json` -> `error_ai_response_invalid`

## OpenAI models
- `openai/models/happy_data_ids.json` -> `success`
- `openai/models/edge_data_names.json` -> `success_with_fallback`
- `openai/models/malformed_data_not_array.json` -> `error_ai_response_invalid`

## OpenAI image generation
- `openai/images-generations/happy_request_id.json` -> `success`
- `openai/images-generations/edge_url_in_data_array.json` -> `success_with_fallback`
- `openai/images-generations/malformed_missing_request_id.json` -> `error_ai_response_invalid`

## Ollama chat
- `ollama/chat/happy_message_content.json` -> `success`
- `ollama/chat/edge_openai_compatible_fallback.json` -> `success_with_fallback`
- `ollama/chat/malformed_message_type_mismatch.json` -> `error_ai_response_invalid`

## Ollama tags
- `ollama/tags/happy_models_names.json` -> `success`
- `ollama/tags/edge_duplicate_latest_suffix.json` -> `success_with_fallback`
- `ollama/tags/malformed_models_not_array.json` -> `error_ai_response_invalid`

## Image polling
- `image-polling/happy_result_url_ready.json` -> `success`
- `image-polling/edge_state_output_url.json` -> `success_with_fallback`
- `image-polling/edge_url_only.json` -> `success_with_fallback`
- `image-polling/terminal_failed_status.json` -> `error_ai_provider`
- `image-polling/malformed_invalid_json.json` -> `error_ai_response_invalid`

## UI autofill payload
- `ui/autofill/happy_strict_contract.json` -> `success`
- `ui/autofill/edge_complexity_string.json` -> `success_with_fallback`
- `ui/autofill/edge_markdown_fenced_json.txt` -> `success_with_fallback`
- `ui/autofill/malformed_missing_required_fields.json` -> `error_validation_required`
- `ui/autofill/malformed_complexity_out_of_range.json` -> `error_validation_invalid`
