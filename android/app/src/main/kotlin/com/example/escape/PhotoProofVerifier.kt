package com.example.escape

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Extracted evidence ONLY. No photo data leaves this device. */
data class ProofEvidence(
    val passed: Boolean,
    val message: String,
    val extractedText: String = "",
    val labels: List<String> = emptyList()
)

class PhotoProofVerifier(private val context: Context) {
    private val expectedLabels = mapOf(
        "grass" to listOf("grass", "lawn", "meadow", "vegetation", "field", "plant", "greenery"),
        "tree" to listOf("tree", "plant", "leaf", "forest", "wood", "branch"),
        "water" to listOf("water", "lake", "river", "canal", "pond", "sea", "reflection"),
        "sky" to listOf("sky", "cloud", "sunset", "sunrise"),
        "flower" to listOf("flower", "plant", "petal", "garden"),
        "nature" to listOf("tree", "plant", "grass", "leaf", "flower", "sky",
            "cloud", "landscape", "forest", "garden", "river", "water", "outdoor")
    )

    fun verify(uri: Uri, tag: String, code: String,
               finished: (ProofEvidence) -> Unit) {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            finished(ProofEvidence(false, "Unable to open picture: ${e.message}"))
            return
        }
        if (tag == "writing") {
            verifyWrittenProof(image, code, finished)
        } else {
            verifyNatureProof(image, tag, finished)
        }
    }

    private fun verifyWrittenProof(image: InputImage, code: String,
                                    finished: (ProofEvidence) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                val normalized = text.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim()
                val codeWords = code.uppercase().split(" ").filter { it.isNotBlank() }
                val hasCode = codeWords.size == 2 &&
                    codeWords.all { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(normalized) }
                val words = normalized.split(Regex("\\s+")).filter { it.length >= 2 }
                val enoughWriting = words.size >= 20
                finished(
                    ProofEvidence(
                        passed = hasCode && enoughWriting,
                        message = if (hasCode && enoughWriting)
                            "Offline text check passed."
                        else if (!hasCode)
                            "Please write the displayed proof words clearly on the page."
                        else "Please write at least 20 readable words, then take a clearer photo.",
                        extractedText = text
                    )
                )
            }
            .addOnFailureListener { e ->
                finished(ProofEvidence(false, "Offline text recognition failed: ${e.message}"))
            }
            .addOnCompleteListener { recognizer.close() }
    }

    private fun verifyNatureProof(image: InputImage, tag: String,
                                   finished: (ProofEvidence) -> Unit) {
        val expected = expectedLabels[tag]
        if (expected == null) {
            finished(ProofEvidence(false, "Unrecognised mission proof type."))
            return
        }
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.42f).build()
        )
        labeler.process(image)
            .addOnSuccessListener { results ->
                val labels = results.map { it.text.lowercase() }
                val match = labels.any { label ->
                    expected.any { keyword -> label.contains(keyword) }
                }
                finished(ProofEvidence(
                    match,
                    if (match) "Nature photo matched the mission."
                    else "I couldn't recognise $tag. Try another clear photo of the requested subject.",
                    labels = labels.take(12)
                ))
            }
            .addOnFailureListener { e ->
                finished(ProofEvidence(false, "Offline image check failed: ${e.message}"))
            }
            .addOnCompleteListener { labeler.close() }
    }
}
