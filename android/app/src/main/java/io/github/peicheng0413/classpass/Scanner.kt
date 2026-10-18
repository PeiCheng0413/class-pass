package io.github.peicheng0413.classpass

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** 掃描畫面上顯示的最近一次結果。id 每次都不同，畫面用它判斷「這是新的一筆」。 */
sealed interface ScanEvent {
    val id: Long

    data class Accepted(override val id: Long, val pass: Pass, val outcomes: List<SaveOutcome>) : ScanEvent
    data class Rejected(override val id: Long, val reason: String) : ScanEvent
}

/**
 * 連續掃描的邏輯：驗證、存檔、回饋，並擋掉重複。
 * 同一張 QR code 只要還在鏡頭前（或離開不到 5 秒）就不再處理，避免一直嗶。
 */
class ScanSession(private val store: Store, private val feedback: Feedback) {
    var last by mutableStateOf<ScanEvent?>(null)
        private set
    var accepted by mutableIntStateOf(0)
        private set

    private val recent = HashMap<String, Long>()
    private var nextId = 0L

    fun onCode(raw: String?, bytes: ByteArray?) {
        val fromBytes = bytes?.let { String(it, Charsets.UTF_8) }
        val key = raw ?: fromBytes ?: return
        val now = SystemClock.elapsedRealtime()
        val prev = recent.put(key, now)
        recent.entries.removeAll { now - it.value > 60_000 }
        if (prev != null && now - prev < 5_000) return

        // ML Kit 偶爾會把中文用別的編碼解讀，rawValue 驗不過時再用原始位元組當 UTF-8 試一次
        val results = listOfNotNull(raw, fromBytes).distinct().map(Protocol::parse)
        when (val r = results.firstOrNull { it is ParseResult.Valid } ?: results.first()) {
            is ParseResult.Valid -> {
                val outcomes = store.save(r.pass, System.currentTimeMillis() / 1000)
                accepted++
                last = ScanEvent.Accepted(++nextId, r.pass, outcomes)
                feedback.ok()
            }
            is ParseResult.Invalid -> {
                last = ScanEvent.Rejected(++nextId, r.reason)
                feedback.error()
            }
        }
    }

    fun dismiss(id: Long) {
        if (last?.id == id) last = null
    }
}

/** 相機預覽＋QR code 辨識。畫面存在期間相機一直開著，每辨識到一個 QR 就呼叫 onCode。 */
@Composable
fun CameraPreview(onCode: (String?, ByteArray?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                analyze(proxy, scanner) { raw, bytes -> if (!disposed) currentOnCode(raw, bytes) }
            }
            p.unbindAll()
            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@OptIn(ExperimentalGetImage::class)
private fun analyze(proxy: ImageProxy, scanner: BarcodeScanner, onCode: (String?, ByteArray?) -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    try {
        scanner.process(input)
            .addOnSuccessListener { codes -> codes.forEach { onCode(it.rawValue, it.rawBytes) } }
            .addOnCompleteListener { proxy.close() }
    } catch (e: IllegalStateException) {
        // 畫面關掉後 scanner 已 close，丟掉這張影像即可
        proxy.close()
    }
}
