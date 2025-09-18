package com.example.notescanner

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

object OcrUtil {
    fun recognizeTextFromImage(context: Context, imageFile: File, onResult: (String) -> Unit) {
        val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                onResult(visionText.text)
            }
            .addOnFailureListener { e ->
                onResult("Villa: ${e.message}")
            }
    }
}
