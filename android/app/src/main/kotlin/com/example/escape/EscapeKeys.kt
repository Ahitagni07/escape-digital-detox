package com.example.escape

object EscapeKeys {
    const val PREFS_NAME = "escape_prefs"

    const val SELECTED_PACKAGES = "selected_packages"
    const val USAGE_MINUTES = "usage_minutes" // Kept for backwards compatibility.
    const val WALK_MINUTES = "walk_minutes"
    const val MIN_STEPS = "min_steps"
    const val ACCESS_MINUTES = "access_minutes"
    const val DEMO_MODE = "demo_mode"

    const val USAGE_SECONDS_TARGET = "usage_seconds_target" // Legacy.
    const val WALK_SECONDS_TARGET = "walk_seconds_target"
    const val EFFECTIVE_MIN_STEPS = "effective_min_steps"
    const val ACCESS_SECONDS_TARGET = "access_seconds_target"

    const val RUNNING = "running"
    const val LOCKED = "locked"
    const val LOCK_STARTED_MS = "lock_started_ms"
    const val LOCK_MODE = "lock_mode"
    const val EVENING_UNLOCK_AT_MS = "evening_unlock_at_ms" // Legacy v5.
    const val ACCESS_UNTIL_MS = "access_until_ms"
    const val MISSION_ACTIVE = "mission_active"
    const val NEXT_MISSION_REMINDER_MS = "next_mission_reminder_ms"
    const val SOCIAL_SECONDS = "social_seconds" // Legacy.
    const val WALK_SECONDS = "walk_seconds"
    const val WALK_STEPS = "walk_steps"
    const val STEP_SENSOR_AVAILABLE = "step_sensor_available"
    const val FOREGROUND_PACKAGE = "foreground_package"

    const val LOCK_MODE_WALK = "walk"
    const val LOCK_MODE_EVENING = "evening"

    const val INTERRUPTIONS = "interruptions"
    const val MISSIONS_COMPLETED = "missions_completed"
    const val WALKS_COMPLETED = "walks_completed"
    const val RECLAIMED_MINUTES = "reclaimed_minutes"
    const val STEPS_EARNED = "steps_earned"
    const val STREAK_DAYS = "streak_days"
    const val LAST_COMPLETION_DATE = "last_completion_date"
    const val EMERGENCY_UNLOCKS_TODAY = "emergency_unlocks_today"
    const val EMERGENCY_DATE = "emergency_date"

    // Local AI/model state.
    const val AI_MODEL_ORIGINAL_NAME = "ai_model_original_name"
    const val AI_LAST_ERROR = "ai_last_error"
    const val AI_ENGINE_READY = "ai_engine_ready"
    const val AI_ENGINE_PREPARING = "ai_engine_preparing"
    const val AI_DOWNLOAD_ID = "ai_download_id"

    // Current mission.
    const val MISSION_TITLE = "mission_title"
    const val MISSION_INSTRUCTION = "mission_instruction"
    const val MISSION_SOURCE = "mission_source"
    const val MISSION_GENERATING = "mission_generating"
    const val MISSION_PROOF_TAG = "mission_proof_tag"
    const val MISSION_VARIANT_INDEX = "mission_variant_index"
    const val ACCESS_UNTIL_ELAPSED = "access_until_elapsed"
    const val ACCESS_ISSUED_ELAPSED = "access_issued_elapsed"
    const val MISSION_START_ELAPSED = "mission_start_elapsed"
    const val LAST_REMINDER_ELAPSED = "last_reminder_elapsed"
    const val CLOCK_ANCHOR_WALL = "clock_anchor_wall"
    const val CLOCK_ANCHOR_ELAPSED = "clock_anchor_elapsed"
    const val CLOCK_BOOT_COUNT = "clock_boot_count"
    const val ACCESS_ISSUED_BOOT_COUNT = "access_issued_boot_count"
    const val LAST_MISSION_TITLE = "last_mission_title"

    // Cached AI missions. They are generated locally in batches so Gemma does not
    // need to run for every notification.
    const val DAY_MISSION_POOL_JSON = "day_mission_pool_json"
    const val EVENING_MISSION_POOL_JSON = "evening_mission_pool_json"

    const val ACTION_TEST_LOCK = "com.example.escape.TEST_LOCK"
    const val ACTION_START_MISSION = "com.example.escape.START_MISSION"
    const val ACTION_PHOTO_APPROVED = "com.example.escape.PHOTO_APPROVED"
    const val ACTION_EMERGENCY_UNLOCK = "com.example.escape.EMERGENCY_UNLOCK"
}
