/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics.export

/**
 * UI-facing seam over the export core. Worker A's ExportSerializer
 * satisfies this contract; the coordinator integrates both branches and
 * removes whichever duplicate survives merge review.
 */
interface ExportUseCase {
    /** Serializes all user data to a single JSON document string. */
    suspend fun buildExport(): String
}
