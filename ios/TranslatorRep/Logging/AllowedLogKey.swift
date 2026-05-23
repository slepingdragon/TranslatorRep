// AllowedLogKey.swift
// TranslatorRep — Story 1.5
//
// The complete allowlist of log field keys that can be passed through SafeLog.event.
// Adding a new key requires an ADR amendment to architecture.md §14 plus a
// simultaneous Android + iOS PR — single-platform additions are rejected by review.
//
// Conversation content (source_text, target_text, caption_text), participant names
// (participant_name, display_name), and Flores-200 language codes (ind_Latn, etc.)
// are FORBIDDEN everywhere — they MUST NOT appear in this enum and MUST NOT be
// passed as the value argument to SafeLog.event.
//
// The raw value (rawValue: String) is the snake_case wire form that lands in
// Crashlytics dashboards and matches the Data Channel convention (/shared/canonical-names.md §2).
// This means the Swift case name (camelCase) and Kotlin enum entry (SCREAMING_SNAKE_CASE)
// both map to the same wire-form String — kept identical across platforms.

import Foundation

enum AllowedLogKey: String {
    /// Canonical Call.id (ULID); wire form `call_id`.
    case callId = "call_id"

    /// Canonical Utterance.id (ULID); wire form `utterance_id`.
    case utteranceId = "utterance_id"

    /// Provider implementation name, e.g. "WhisperCppAsrProvider"; wire form `provider_name`.
    case providerName = "provider_name"

    /// Model artifact name, e.g. "nllb-200-distilled-600M-int8"; wire form `model_name`.
    case modelName = "model_name"

    /// Operation latency in milliseconds; wire form `latency_ms`.
    case latencyMs = "latency_ms"

    /// ERR_*/WARN_*/INFO_* code from ErrorCode; wire form `error_code`.
    case errorCode = "error_code"

    /// Data Channel schema version (current = 1); wire form `schema_version`.
    case schemaVersion = "schema_version"

    /// Source language BCP 47 code (e.g. "id-ID"); wire form `source_lang`.
    case sourceLang = "source_lang"

    /// Target language BCP 47 code (e.g. "en-US"); wire form `target_lang`.
    case targetLang = "target_lang"

    /// RoomState name; wire form `room_state`.
    case roomState = "room_state"

    /// Network type bucket ("wifi" / "cellular" / "unknown"); wire form `network_type`.
    case networkType = "network_type"

    /// Caption render latency in milliseconds; wire form `caption_render_latency_ms`.
    case captionRenderLatencyMs = "caption_render_latency_ms"

    /// Count of TranslationStatus transitions in a call; wire form `translation_status_transition_count`.
    case translationStatusTransitionCount = "translation_status_transition_count"

    /// Video pause duration bucket label; wire form `video_pause_duration_bucket`.
    case videoPauseDurationBucket = "video_pause_duration_bucket"

    /// Median inter-utterance gap in ms; wire form `inter_turn_gap_median_ms`.
    case interTurnGapMedianMs = "inter_turn_gap_median_ms"

    /// p95 inter-utterance gap in ms; wire form `inter_turn_gap_p95_ms`.
    case interTurnGapP95Ms = "inter_turn_gap_p95_ms"

    /// Call duration in ms; wire form `call_duration_ms`.
    case callDurationMs = "call_duration_ms"
}
