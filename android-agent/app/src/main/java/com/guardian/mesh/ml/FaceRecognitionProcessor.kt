package com.guardian.mesh.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceRecognitionProcessor(context: Context) {
    private var interpreter: Interpreter? = null
    private var inputImageSize = 112 
    private val embeddingSize = 192
    private var batchSize = 1

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "MobileFaceNet.tflite")
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(model, options)
            
            val inputTensor = interpreter?.getInputTensor(0)
            val inputShape = inputTensor?.shape()
            
            if (inputShape != null) {
                // Shape is usually [Batch, Height, Width, Channels]
                if (inputShape.size == 4) {
                    batchSize = inputShape[0]
                    inputImageSize = inputShape[1]
                }
                Log.d("FaceRec", "Model Input Shape: ${inputShape.contentToString()} (Batch=$batchSize)")
            }
        } catch (e: Exception) {
            Log.e("FaceRec", "Error loading model", e)
        }
    }

    fun getFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        if (interpreter == null) return FloatArray(embeddingSize)

        // 1. Preprocess: Resize
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputImageSize, inputImageSize, true)
        
        // 2. Preprocess: Convert to ByteBuffer (Handling Batch Size)
        val inputBuffer = convertBitmapToByteBuffer(resized)
        
        // 3. Run Inference
        // Output shape must match model output: [Batch, EmbeddingSize]
        val outputBuffer = Array(batchSize) { FloatArray(embeddingSize) }
        interpreter?.run(inputBuffer, outputBuffer)
        
        return l2Normalize(outputBuffer[0])
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputTensor = interpreter?.getInputTensor(0)
        // Check DataType if possible, defaulting to Float32
        // We avoid checking DataType.FLOAT64 directly as it might be missing in some SDK versions
        val numElements = batchSize * inputImageSize * inputImageSize * 3
        val tensorBytes = inputTensor?.numBytes() ?: -1
        val isDouble = (tensorBytes == numElements * 8)

        val bytesPerChannel = if (isDouble) 8 else 4
        val pixelsPerImage = inputImageSize * inputImageSize * 3
        
        // Allocate for FULL Batch
        val byteBuffer = ByteBuffer.allocateDirect(batchSize * pixelsPerImage * bytesPerChannel)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(inputImageSize * inputImageSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // Fill Batch 0 with Image
        for (i in 0 until batchSize) {
            // For batch > 0, we can copy the same image or zeros. 
            // Copying same image is safer if model expects pairs.
            for (pixelValue in intValues) {
                val r = ((pixelValue shr 16 and 0xFF) - 128f) / 128f
                val g = ((pixelValue shr 8 and 0xFF) - 128f) / 128f
                val b = ((pixelValue and 0xFF) - 128f) / 128f
                
                if (isDouble) {
                    byteBuffer.putDouble(r.toDouble())
                    byteBuffer.putDouble(g.toDouble())
                    byteBuffer.putDouble(b.toDouble())
                } else {
                    byteBuffer.putFloat(r)
                    byteBuffer.putFloat(g)
                    byteBuffer.putFloat(b)
                }
            }
        }
        
        return byteBuffer
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (f in embedding) sum += f * f
        val norm = kotlin.math.sqrt(sum)
        return if (norm > 0) {
            FloatArray(embeddingSize) { i -> embedding[i] / norm }
        } else {
            embedding
        }
    }
}
