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
                val check = MissionProofRules.checkWrittenProof(text, code)
                finished(
                    ProofEvidence(
                        passed = check.passed,
                        message = when {
                            check.passed -> "Offline text check passed."
                            !check.hasCode ->
                                "Please write the displayed proof words clearly on the page."
                            else ->
                                "Please write at least 20 readable words, then take a clearer photo."
                        },
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
        if (!MissionProofRules.isSupportedNatureSubject(tag)) {
            finished(ProofEvidence(false, "Unrecognised mission proof type."))
            return
        }
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.42f).build()
        )
        labeler.process(image)
            .addOnSuccessListener { results ->
                val labels = results.map { it.text.lowercase() }
                val match = MissionProofRules.matchesNatureSubject(tag, labels) == true
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
