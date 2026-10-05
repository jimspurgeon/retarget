/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CreativeManifestLoaderTest {
    private val validManifestJson =
        """
        {
          "packId": "test-pack",
          "images": [
            {
              "unsplashId": "ABC123",
              "file": "photographer-ABC123-unsplash.jpg",
              "subTheme": "test theme",
              "photographer": "Test Photographer",
              "photographerUrl": "https://unsplash.com/@test",
              "license": "Unsplash License",
              "licenseUrl": "https://unsplash.com/license",
              "sourceUrl": "https://unsplash.com/photos/ABC123",
              "width": 2160,
              "height": 1441,
              "sha256": "abc123def456"
            },
            {
              "unsplashId": "XYZ789",
              "file": "photographer-XYZ789-unsplash.jpg",
              "photographer": "Another Photographer",
              "licenseUrl": "https://unsplash.com/license",
              "sha256": "xyz789ghi012"
            }
          ]
        }
        """.trimIndent()

    private val minimalValidManifest =
        """
        {
          "packId": "minimal",
          "images": [
            {
              "unsplashId": "MIN001",
              "file": "min.jpg",
              "photographer": "Min",
              "licenseUrl": "https://example.com",
              "sha256": "abc"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `parses valid manifest successfully`() {
        val loader = CreativeManifestLoader()
        val (packId, creatives) = loader.loadManifest(validManifestJson)

        assertEquals("test-pack", packId)
        assertEquals(2, creatives.size)

        val first = creatives[0]
        assertEquals("ABC123", first.unsplashId)
        assertEquals("photographer-ABC123-unsplash.jpg", first.imagePath)
        assertEquals("Test Photographer", first.attribution)
        assertEquals("https://unsplash.com/license", first.licenseUrl)
        assertEquals("abc123def456", first.sha256)
        assertEquals("test theme", first.subTheme)

        val second = creatives[1]
        assertEquals("XYZ789", second.unsplashId)
        assertEquals("default", second.subTheme) // subTheme is optional
    }

    @Test
    fun `accepts manifest with optional fields omitted`() {
        val loader = CreativeManifestLoader()
        val (packId, creatives) = loader.loadManifest(minimalValidManifest)

        assertEquals("minimal", packId)
        assertEquals(1, creatives.size)
        assertNotNull(creatives[0].id) // ID should be generated
    }

    @Test
    fun `rejects manifest with missing packId`() {
        val loader = CreativeManifestLoader()
        val invalidJson = """{"images": []}"""

        try {
            loader.loadManifest(invalidJson)
            fail("Expected ManifestValidationException")
        } catch (e: CreativeManifestLoader.ManifestValidationException) {
            assertTrue(e.errors.any { "packId" in it })
        }
    }

    @Test
    fun `rejects manifest with empty images array`() {
        val loader = CreativeManifestLoader()
        val invalidJson = """{"packId": "x", "images": []}"""

        try {
            loader.loadManifest(invalidJson)
            fail("Expected ManifestValidationException")
        } catch (e: CreativeManifestLoader.ManifestValidationException) {
            assertTrue(e.errors.any { "images" in it })
        }
    }

    @Test
    fun `rejects image missing required fields`() {
        val loader = CreativeManifestLoader()
        val invalidJson =
            """
            {
              "packId": "test",
              "images": [
                {"unsplashId": "X"}
              ]
            }
            """.trimIndent()

        try {
            loader.loadManifest(invalidJson)
            fail("Expected ManifestValidationException")
        } catch (e: CreativeManifestLoader.ManifestValidationException) {
            assertTrue(e.errors.any { "file" in it })
            assertTrue(e.errors.any { "photographer" in it })
            assertTrue(e.errors.any { "licenseUrl" in it })
            assertTrue(e.errors.any { "sha256" in it })
        }
    }

    @Test
    fun `rejects duplicate unsplashId within pack`() {
        val loader = CreativeManifestLoader()
        val invalidJson =
            """
            {
              "packId": "test",
              "images": [
                {
                  "unsplashId": "DUP123",
                  "file": "a.jpg",
                  "photographer": "A",
                  "licenseUrl": "https://x",
                  "sha256": "a"
                },
                {
                  "unsplashId": "DUP123",
                  "file": "b.jpg",
                  "photographer": "B",
                  "licenseUrl": "https://x",
                  "sha256": "b"
                }
              ]
            }
            """.trimIndent()

        try {
            loader.loadManifest(invalidJson)
            fail("Expected ManifestValidationException for duplicate")
        } catch (e: CreativeManifestLoader.ManifestValidationException) {
            assertTrue(e.errors.any { "duplicate" in it })
        }
    }

    @Test
    fun `validates file existence when path checker provided`() {
        val missingFileLoader =
            CreativeManifestLoader { path ->
                path.endsWith("exists.jpg")
            }

        val jsonWithMissingFile =
            """
            {
              "packId": "test",
              "images": [
                {
                  "unsplashId": "M1",
                  "file": "missing.jpg",
                  "photographer": "P",
                  "licenseUrl": "https://x",
                  "sha256": "s"
                },
                {
                  "unsplashId": "M2",
                  "file": "exists.jpg",
                  "photographer": "P2",
                  "licenseUrl": "https://x",
                  "sha256": "s2"
                }
              ]
            }
            """.trimIndent()

        try {
            missingFileLoader.loadManifest(jsonWithMissingFile, "/assets/pack")
            fail("Expected ManifestValidationException for missing file")
        } catch (e: CreativeManifestLoader.ManifestValidationException) {
            assertTrue(e.errors.any { "missing.jpg" in it && "not found" in it })
        }
    }

    @Test
    fun `validateOnly returns Valid for good manifest`() {
        val loader = CreativeManifestLoader()
        val result = loader.validateOnly(validManifestJson)

        assertTrue(result is CreativeManifestLoader.ValidationResult.Valid)
    }

    @Test
    fun `validateOnly returns Invalid with errors for bad manifest`() {
        val loader = CreativeManifestLoader()
        val result = loader.validateOnly("""{"images":[]}""")

        assertTrue(result is CreativeManifestLoader.ValidationResult.Invalid)
        val invalid = result as CreativeManifestLoader.ValidationResult.Invalid
        assertTrue(invalid.errors.any { "packId" in it })
        assertTrue(invalid.errors.any { "images" in it })
    }

    @Test
    fun `handles subTheme metadata correctly`() {
        val loader = CreativeManifestLoader()
        val json =
            """
            {
              "packId": "theme-test",
              "images": [
                {
                  "unsplashId": "T1",
                  "file": "t1.jpg",
                  "subTheme": "misty mountain",
                  "photographer": "P",
                  "licenseUrl": "https://x",
                  "sha256": "s"
                },
                {
                  "unsplashId": "T2",
                  "file": "t2.jpg",
                  "photographer": "P2",
                  "licenseUrl": "https://x",
                  "sha256": "s2"
                }
              ]
            }
            """.trimIndent()

        val (_, creatives) = loader.loadManifest(json)

        assertEquals("misty mountain", creatives[0].subTheme)
        assertEquals("default", creatives[1].subTheme)
    }
}
