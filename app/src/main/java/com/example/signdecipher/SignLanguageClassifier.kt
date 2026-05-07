package com.example.signdecipher

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SignLanguageClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val modelFile = "sign_language_model.tflite"
    private val labelFile = "labels.txt"
    private var labels: List<String> = emptyList()

    init {
        setupClassifier()
    }

    private fun setupClassifier() {
        try {
            val options = Interpreter.Options()
            interpreter = Interpreter(loadModelFile(), options)
            labels = loadLabels()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(labelFile).bufferedReader().readLines()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun classify(landmarks: List<Float>): String {
        if (interpreter == null) return "Model not loaded"

        // Prepare input. Assuming the model expects a 1D array of landmarks.
        // Adjust the shape here if your model expects [1, 42] or [1, 63] etc.
        val input = Array(1) { FloatArray(landmarks.size) }
        for (i in landmarks.indices) {
            input[0][i] = landmarks[i]
        }

        // Prepare output. Assuming the output is a probability distribution over the classes.
        val output = Array(1) { FloatArray(labels.size) }

        interpreter?.run(input, output)

        // Find the index with the highest probability
        val probabilities = output[0]
        var maxIndex = -1
        var maxProb = 0.0f

        for (i in probabilities.indices) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i]
                maxIndex = i
            }
        }

        return if (maxIndex != -1 && maxIndex < labels.size) {
            labels[maxIndex]
        } else {
            "Unknown"
        }
    }

    fun close() {
        interpreter?.close()
    }
}
