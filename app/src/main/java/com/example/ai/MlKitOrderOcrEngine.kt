package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.model.OrderInputSource
import com.example.model.ParsedOrderDraft
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed class OcrScanOutcome {
    data class Success(val draft: ParsedOrderDraft) : OcrScanOutcome()
    data class ProtectedOrBlankScreen(val messageTh: String) : OcrScanOutcome()
    data class Failure(val reasonTh: String) : OcrScanOutcome()
}

/**
 * Local on-device OCR processor backed by Google ML Kit Text Recognition.
 * Runs entirely offline on background dispatchers and detects protected (FLAG_SECURE) or blank frames safely.
 */
object MlKitOrderOcrEngine {

    /**
     * Checks whether a captured bitmap is effectively black/uniform (typical of FLAG_SECURE protected screens).
     */
    fun isLikelyProtectedOrBlankBitmap(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 4 || h <= 4) return true

        val stepX = (w / 16).coerceAtLeast(1)
        val stepY = (h / 16).coerceAtLeast(1)
        var sampled = 0
        var darkOrUniform = 0
        val firstPixel = bitmap.getPixel(w / 2, h / 2)
        var sameAsFirst = 0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = bitmap.getPixel(x, y)
                sampled++
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                val a = Color.alpha(px)
                if (a < 16 || (r < 10 && g < 10 && b < 10)) {
                    darkOrUniform++
                }
                if (px == firstPixel) {
                    sameAsFirst++
                }
                x += stepX
            }
            y += stepY
        }

        if (sampled == 0) return true
        val darkRatio = darkOrUniform.toFloat() / sampled
        val uniformRatio = sameAsFirst.toFloat() / sampled
        return darkRatio >= 0.97f || uniformRatio >= 0.985f
    }

    suspend fun recognizeBitmap(
        bitmap: Bitmap,
        source: OrderInputSource
    ): OcrScanOutcome = withContext(Dispatchers.Default) {
        if (isLikelyProtectedOrBlankBitmap(bitmap)) {
            return@withContext OcrScanOutcome.ProtectedOrBlankScreen(
                "ตรวจพบหน้าจอที่ถูกป้องกัน (FLAG_SECURE) หรือภาพว่างเปล่า ไม่สามารถอ่านข้อความได้ แนะนำใช้วิธีนำเข้าภาพหน้าจอหรือกรอกข้อมูลด้วยตนเอง"
            )
        }

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val text = runMlKitTextRecognizer(inputImage)
            if (text.isBlank()) {
                return@withContext OcrScanOutcome.Failure(
                    "ไม่พบข้อความที่อ่านได้ในภาพ กรุณาตรวจสอบความคมชัดหรือกรอกข้อมูลงานด้วยตนเอง"
                )
            }
            val draft = OrderOcrParser.parseOrderText(text, source)
            OcrScanOutcome.Success(draft)
        } catch (e: Throwable) {
            OcrScanOutcome.Failure(
                "การอ่านข้อความ OCR ขัดข้อง (${e.localizedMessage ?: "ไม่สามารถประมวลผลภาพได้"}) — สามารถกรอกข้อมูลงานด้วยตนเองได้ทันที"
            )
        }
    }

    suspend fun recognizeImageUri(
        context: Context,
        uri: Uri,
        source: OrderInputSource
    ): OcrScanOutcome = withContext(Dispatchers.IO) {
        val bitmap = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Throwable) {
            return@withContext OcrScanOutcome.Failure(
                "ไม่สามารถเปิดไฟล์ภาพได้ (${e.localizedMessage ?: "สิทธิ์หรือไฟล์ไม่ถูกต้อง"})"
            )
        }

        if (bitmap == null) {
            return@withContext OcrScanOutcome.Failure("ไม่สามารถถอดรหัสภาพที่เลือกได้ กรุณาลองเลือกภาพหน้าจอใหม่")
        }

        try {
            recognizeBitmap(bitmap, source)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private suspend fun runMlKitTextRecognizer(inputImage: InputImage): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    runCatching { recognizer.close() }
                    if (cont.isActive) {
                        cont.resume(visionText.text.orEmpty())
                    }
                }
                .addOnFailureListener { err ->
                    runCatching { recognizer.close() }
                    if (cont.isActive) {
                        cont.resume("")
                    }
                }
                .addOnCanceledListener {
                    runCatching { recognizer.close() }
                    if (cont.isActive) {
                        cont.resume("")
                    }
                }
        }
}
