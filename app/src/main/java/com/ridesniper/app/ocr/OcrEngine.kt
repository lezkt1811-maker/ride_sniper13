package com.ridesniper.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Thin coroutine wrapper around ML Kit's on-device Latin text recognizer.
 * Everything runs locally; no image or text ever leaves the device.
 */
class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs OCR on [bitmap] and returns the recognized text plus a heuristic
     * confidence score (ML Kit's Latin recognizer doesn't expose a single
     * scalar confidence, so we derive one from block/line density and average
     * element confidence where available).
     */
    suspend fun recognize(bitmap: Bitmap): Pair<String, Float> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        val text = result.text
        val blocks = result.textBlocks
        val confidence = estimateConfidence(blocks.size, text.length)
        return text to confidence
    }

    private fun estimateConfidence(blockCount: Int, textLength: Int): Float {
        if (textLength == 0) return 0f
        // More recognized blocks and reasonable text length both correlate with a
        // clean, well-lit capture of a normal Uber offer card.
        val blockScore = (blockCount / 8f).coerceIn(0f, 1f)
        val lengthScore = (textLength / 120f).coerceIn(0f, 1f)
        return ((blockScore * 0.5f) + (lengthScore * 0.5f)).coerceIn(0.2f, 1f)
    }

    fun close() {
        recognizer.close()
    }
}
