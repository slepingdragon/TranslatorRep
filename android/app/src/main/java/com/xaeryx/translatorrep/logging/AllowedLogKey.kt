package com.xaeryx.translatorrep.logging

/**
 * The complete allowlist of log field keys that can be passed through [SafeLog.event].
 * Adding a new key requires an ADR amendment to architecture.md §14 plus a simultaneous
 * Android + iOS PR — single-platform additions are rejected by code review.
 *
 * Conversation content (`source_text`, `target_text`, `caption_text`), participant
 * names (`participant_name`, `display_name`), and Flores-200 language codes (e.g.
 * `ind_Latn`) are **forbidden everywhere** — they MUST NOT appear in this enum and
 * MUST NOT be passed as the `value` argument to [SafeLog.event].
 *
 * See architecture.md §14 (Logging — SafeLog Facade + Explicit Allowlist).
 */
enum class AllowedLogKey {
    /** Canonical [Call.id] (ULID); wire form `call_id`. */
    CALL_ID,

    /** Canonical [Utterance.id] (ULID); wire form `utterance_id`. */
    UTTERANCE_ID,

    /** Provider implementation name, e.g. "Nllb200OnnxProvider"; wire form `provider_name`. */
    PROVIDER_NAME,

    /** Model artifact name, e.g. "nllb-200-distilled-600M-int8"; wire form `model_name`. */
    MODEL_NAME,

    /** Operation latency in milliseconds; wire form `latency_ms`. */
    LATENCY_MS,

    /** Error/warn/info code from [ErrorCode] (e.g. `ERR_TRANS_PROVIDER_UNAVAIL`); wire form `error_code`. */
    ERROR_CODE,

    /** Data Channel schema version (current = 1); wire form `schema_version`. */
    SCHEMA_VERSION,

    /** Source language BCP 47 code (e.g. "id-ID"); wire form `source_lang`. */
    SOURCE_LANG,

    /** Target language BCP 47 code (e.g. "en-US"); wire form `target_lang`. */
    TARGET_LANG,

    /** [RoomState] name ("active" / "waitingForPartner" / "ended"); wire form `room_state`. */
    ROOM_STATE,

    /** Network type bucket ("wifi" / "cellular" / "unknown"); wire form `network_type`. */
    NETWORK_TYPE,

    /** Caption render latency in milliseconds; wire form `caption_render_latency_ms`. */
    CAPTION_RENDER_LATENCY_MS,

    /** Count of TranslationStatus state transitions in a call; wire form `translation_status_transition_count`. */
    TRANSLATION_STATUS_TRANSITION_COUNT,

    /** Video pause duration bucket label (e.g. "<5s" / "5-30s" / ">30s"); wire form `video_pause_duration_bucket`. */
    VIDEO_PAUSE_DURATION_BUCKET,

    /** Median inter-utterance gap in milliseconds (per call); wire form `inter_turn_gap_median_ms`. */
    INTER_TURN_GAP_MEDIAN_MS,

    /** p95 inter-utterance gap in milliseconds (per call); wire form `inter_turn_gap_p95_ms`. */
    INTER_TURN_GAP_P95_MS,

    /** Call duration in milliseconds; wire form `call_duration_ms`. */
    CALL_DURATION_MS,

    // ── Story 1.4 (Firebase Init + Firestore + App Check) — added 2026-05-23 ─

    /** Firebase init outcome ("success" | "failed:<ExceptionClass>"); wire form `firebase_init`. */
    FIREBASE_INIT,

    /** App Check provider init outcome ("debug" | "playintegrity" | "failed:<reason>"); wire form `app_check_init`. */
    APP_CHECK_INIT,

    /** Auth UID — first 4 chars ONLY (never full UID per privacy convention); wire form `auth_uid`. */
    AUTH_UID,

    /** Story 1.4 smoke test — self-write to /users/{uid} outcome; wire form `smoke_users_write`. */
    SMOKE_USERS_WRITE,

    /** Story 1.4 smoke test — forbidden-write to /users/<other-uid> outcome (expected "denied"); wire form `smoke_forbidden_write`. */
    SMOKE_FORBIDDEN_WRITE,
}

/**
 * snake_case wire form of an enum entry, derived mechanically from the SCREAMING_SNAKE_CASE
 * name. e.g. `CALL_ID → "call_id"`. This is the shape that lands in Crashlytics dashboards
 * and matches the Data Channel snake_case convention (/shared/canonical-names.md §2).
 */
internal val AllowedLogKey.wireKey: String
    get() = name.lowercase()
