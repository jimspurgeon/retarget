/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses bundled creative pack manifests (JSON) into validated [Creative] objects.
 *
 * Manifest format (produced by scripts/fetch_creatives.py):
 * ```json
 * {
 *   "packId": "fresh-air",
 *   "images": [
 *     {
 *       "unsplashId": "ABC123",
 *       "file": "photographer-ABC123-unsplash.jpg",
 *       "subTheme": "misty mountain peaks layers",
 *       "photographer": "Photographer Name",
 *       "photographerUrl": "https://unsplash.com/@handle",
 *       "license": "Unsplash License",
 *       "licenseUrl": "https://unsplash.com/license",
 *       "sourceUrl": "https://unsplash.com/photos/ABC123",
 *       "width": 2160,
 *       "height": 1441,
 *       "sha256": "abc123..."
 *     }
 *   ]
 * }
 * ```
 *
 * Validation rules (mirroring checkCreativeLicenses in app/build.gradle.kts):
 * - packId present and non-blank
 * - images array non-empty
 * - per-image required fields: unsplashId, file, photographer, licenseUrl, sha256
 * - no duplicate unsplashId within the pack
 * - referenced files exist in assets directory
 *
 * @param fileExists Function to check if a file path exists (inject for testing)
 */
class CreativeManifestLoader(
    private val fileExists: (path: String) -> Boolean = { true },
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class PackManifest(
        val packId: String? = null,
        val images: List<ManifestImage>? = null,
    )

    @Serializable
    private data class ManifestImage(
        val unsplashId: String? = null,
        val file: String? = null,
        val subTheme: String? = null,
        val photographer: String? = null,
        val photographerUrl: String? = null,
        val license: String? = null,
        val licenseUrl: String? = null,
        val sourceUrl: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val sha256: String? = null,
    )

    /**
     * Exception thrown when manifest validation fails.
     * Contains detailed error messages for debugging.
     */
    class ManifestValidationException(
        val packId: String?,
        val errors: List<String>,
    ) : IllegalStateException("Invalid manifest for pack '$packId': ${errors.joinToString("; ")}")

    /**
     * Loads and validates a creative pack manifest from JSON.
     *
     * @param manifestJson Raw JSON string from assets
     * @param packAssetsDir Path to the pack's asset directory (for file existence checks)
     * @return Pair of (packId, list of Creative entities)
     * @throws ManifestValidationException if validation fails
     */
    fun loadManifest(
        manifestJson: String,
        packAssetsDir: String = "",
    ): Pair<String, List<CreativeEntity>> {
        val manifest = json.decodeFromString<PackManifest>(manifestJson)

        val errors = mutableListOf<String>()

        // Validate packId
        val packId = manifest.packId?.takeIf { it.isNotBlank() }
        if (packId == null) {
            errors.add("packId missing or blank")
        }

        // Validate images array
        val images = manifest.images?.takeIf { it.isNotEmpty() }
        if (images.isNullOrEmpty()) {
            errors.add("images array missing or empty")
            if (packId != null) throw ManifestValidationException(packId, errors)
            throw ManifestValidationException(null, errors)
        }

        // Track seen IDs and collect valid entities
        val seenIds = mutableSetOf<String>()
        val validEntities = mutableListOf<CreativeEntity>()

        for ((index, img) in images.withIndex()) {
            val imgErrors = validateImage(img, index, packAssetsDir, seenIds)
            if (imgErrors.isEmpty()) {
                val entity = buildCreativeEntity(packId ?: "unknown", img)
                validEntities.add(entity)
            } else {
                errors.addAll(imgErrors)
            }
        }

        if (errors.isNotEmpty()) {
            throw ManifestValidationException(packId, errors)
        }

        return packId!! to validEntities
    }

    private fun validateImage(
        img: ManifestImage,
        index: Int,
        packAssetsDir: String,
        seenIds: MutableSet<String>,
    ): List<String> {
        val errors = mutableListOf<String>()
        val idLabel = img.unsplashId ?: "(no-id)"

        // Required fields check
        if (img.unsplashId.isNullOrBlank()) {
            errors.add("image[$index]: unsplashId missing")
        }
        if (img.file.isNullOrBlank()) {
            errors.add("image[$index]: file missing")
        }
        if (img.photographer.isNullOrBlank()) {
            errors.add("image[$index]: photographer missing")
        }
        if (img.licenseUrl.isNullOrBlank()) {
            errors.add("image[$index]: licenseUrl missing")
        }
        if (img.sha256.isNullOrBlank()) {
            errors.add("image[$index]: sha256 missing")
        }

        // Duplicate check
        img.unsplashId?.let { id ->
            if (!seenIds.add(id)) {
                errors.add("image[$index]: duplicate unsplashId '$id'")
            }
        }

        // File existence check (only if file is specified and we have a pack dir)
        if (!img.file.isNullOrBlank() && packAssetsDir.isNotBlank()) {
            val filePath = "$packAssetsDir/${img.file}"
            if (!fileExists(filePath)) {
                errors.add("image[$index]/$idLabel: file '${img.file}' not found at '$filePath'")
            }
        }

        return errors
    }

    private fun buildCreativeEntity(
        packId: String,
        img: ManifestImage,
    ): CreativeEntity {
        val copyPool = listOf("Stay inspired.") // Default copy, could come from manifest later

        return CreativeEntity(
            id = img.unsplashId ?: "creative_${System.currentTimeMillis()}_${(0..9).random()}",
            packId = packId,
            subTheme = img.subTheme ?: "default",
            imagePath = img.file ?: "",
            attribution = img.photographer,
            licenseUrl = img.licenseUrl!!,
            sha256 = img.sha256!!,
            baseAppeal = 1.0f,
            copyPoolJson = CopyPoolCodec.encode(copyPool),
            unsplashId = img.unsplashId,
            photographer = img.photographer,
            photographerUrl = img.photographerUrl,
            sourceUrl = img.sourceUrl,
            width = img.width ?: 0,
            height = img.height ?: 0,
        )
    }

    /**
     * Validates a manifest without throwing, returning validation result.
     */
    fun validateOnly(
        manifestJson: String,
        packAssetsDir: String = "",
    ): ValidationResult =
        try {
            loadManifest(manifestJson, packAssetsDir)
            ValidationResult.Valid
        } catch (e: ManifestValidationException) {
            ValidationResult.Invalid(e.packId, e.errors)
        }

    /**
     * Result type for validateOnly().
     */
    sealed interface ValidationResult {
        data object Valid : ValidationResult

        data class Invalid(
            val packId: String?,
            val errors: List<String>,
        ) : ValidationResult
    }
}
