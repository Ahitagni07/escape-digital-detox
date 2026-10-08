package com.example.escape

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

/**
 * Bundled, completely offline visual labeler.
 * The Gemma 3 1B model is text-only: it never sees the image.
 * Images are processed in memory and NOT uploaded/saved by ESCAPE.
 * This checks depicted content, not where/when the picture was taken.
 */
class PhotoProofVerifier(private val context: Context) {
    private val allowed = mapOf(
        "tree" to listOf("tree", "plant", "leaf", "forest", "wood", "branch"),
        "water" to listOf("water", "lake", "river", "canal", "pond", "sea", "reflection"),
        "sky" to listOf("sky", "cloud", "sunset", "sunrise", "blue"),
        "flower" to listOf("flower", "plant", "petal", "garden"),
        "nature" to listOf("tree", "plant", "grass", "leaf", "flower", "sky", "cloud",
            "landscape", "forest", "garden", "river", "water", "outdoor")
    )

    fun verify(uri: Uri, tag: String, finished: (Map<String, Any>) -> Unit) {
        if (!allowed.containsKey(tag)) {
            finished(mapOf("approved" to false, "message" to "This mission uses a timer, not a photo."))
            return
        }
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (error: Exception) {
            finished(mapOf("approved" to false, "message" to "Could not read image: ${error.message}"))
            return
        }
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.42f).build()
        )
        labeler.process(image)
            .addOnSuccessListener { results ->
                val labels = results.map { it.text.lowercase() }
                val expected = allowed[tag].orEmpty()
                val approved = labels.any { label -> expected.any { word ->
                    label.contains(word, ignoreCase = true)
                } }
                finished(mapOf(
                    "approved" to approved,
                    "labels" to labels.take(8),
                    "message" to if (approved) "Offline photo check passed. Enjoy your earned access!"
                                else "Couldn't recognise ${tag} in this photo. Try a clearer photo, or choose the next hourly mission."
                ))
                labeler.close()
            }
            .addOnFailureListener { error ->
                finished(mapOf("approved" to false,
                    "message" to "Image check failed: ${error.message}"))
                labeler.close()
            }
    }
}
