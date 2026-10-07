/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics.export

import kotlinx.serialization.json.Json

/**
 * Serializes an [ExportPayload] to the human-readable JSON that v0.4.0's
 * "Export my data" action writes to a user-chosen location.
 *
 * Formatting choices (AGENTS.md §2 transparency): pretty-printed so the
 * user can open and read their own export in any text editor, and
 * `encodeDefaults` on so every field is explicit rather than implied.
 * Field order is kotlinx declaration order (schemaVersion first), which
 * keeps the wire format stable and diff-friendly.
 */
object ExportSerializer {
    /** The schema version this build emits; bump on any breaking payload change. */
    const val CURRENT_SCHEMA_VERSION: Int = ExportPayload.SCHEMA_VERSION

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

    /** Renders [payload] as a pretty-printed JSON document. Pure, deterministic. */
    fun serialize(payload: ExportPayload): String = json.encodeToString(payload)

    /**
     * Minimal v1 parser used ONLY for round-trip unit tests — there is no
     * import feature in v0.4.0 (maintainer decision, PHASE3-AGENCY.md §8/1;
     * import lands in v0.4.1 and will evolve this function). Not wired to
     * any UI.
     */
    fun deserialize(json: String): ExportPayload = this.json.decodeFromString(json)
}
