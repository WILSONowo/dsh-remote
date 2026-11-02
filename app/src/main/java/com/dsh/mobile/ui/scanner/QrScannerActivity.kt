package com.dsh.mobile.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Offline QR recognition. Camera frames and decoded payloads are never saved or logged. */
class QrScannerActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var torch: Button
    private lateinit var zoom: Button
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var cameraPreview: Preview? = null
    private var scanner: BarcodeScanner? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val finished = AtomicBoolean(false)
    private var torchOn = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (preview.display?.displayId == displayId) {
                val rotation = preview.display.rotation
                analysis?.targetRotation = rotation
                cameraPreview?.targetRotation = rotation
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(ScanGuide(this), FrameLayout.LayoutParams(-1, -1))
        val close = Button(this).apply { text = "返回"; setOnClickListener { finish() } }
        root.addView(close, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        status = TextView(this).apply { setTextColor(Color.WHITE); gravity = Gravity.CENTER; textSize = 14f }
        torch = Button(this).apply { text = "打开补光"; isEnabled = false; setOnClickListener {
            camera?.let { cam ->
                val next = !torchOn
                val operation = cam.cameraControl.enableTorch(next)
                operation.addListener({ runCatching { operation.get() }.onFailure { status.text = "无法切换补光，请重试" } }, ContextCompat.getMainExecutor(this@QrScannerActivity))
            }
        } }
        zoom = Button(this).apply { text = "放大"; isEnabled = false; setOnClickListener {
            camera?.let { cam ->
                val state = cam.cameraInfo.zoomState.value ?: return@let
                setZoom(if (state.zoomRatio > 1.1f) 1f else minOf(2f, state.maxZoomRatio))
            }
        } }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(LinearLayout(this@QrScannerActivity).apply { gravity = Gravity.CENTER; addView(torch); addView(zoom) })
        }
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, android.os.Handler(mainLooper))
        preview.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) { focus(event.x, event.y); view.performClick() }
            true
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "需要相机权限才能扫码，请返回后重试"; return
        }
        startCamera()
    }

    private fun focus(x: Float, y: Float) {
        val cam = camera ?: return
        if (preview.width == 0 || preview.height == 0) return
        val point = preview.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        if (cam.cameraInfo.isFocusMeteringSupported(action)) cam.cameraControl.startFocusAndMetering(action)
    }

    private fun setZoom(ratio: Float): Boolean {
        val cam = camera ?: return false
        val state = cam.cameraInfo.zoomState.value ?: return false
        if (finished.get() || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return false
        cam.cameraControl.setZoomRatio(ratio.coerceIn(state.minZoomRatio, minOf(4f, state.maxZoomRatio)))
        return true
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun startCamera() {
        status.text = "正在打开相机…"
        val future = ProcessCameraProvider.getInstance(this)
        val main = ContextCompat.getMainExecutor(this)
        future.addListener({
            if (isDestroyed || isFinishing) return@addListener
            try {
                val cameraProvider = future.get(); provider = cameraProvider
                val rotation = preview.display?.rotation ?: Surface.ROTATION_0
                val cameraFeed = Preview.Builder().setTargetRotation(rotation).build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val analyzer = ImageAnalysis.Builder().setTargetRotation(rotation)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis = analyzer; cameraPreview = cameraFeed
                val cam = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, cameraFeed, analyzer)
                camera = cam
                val maxZoom = minOf(4f, cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)
                val decoder = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .setZoomSuggestionOptions(ZoomSuggestionOptions.Builder { ratio -> setZoom(ratio) }.setMaxSupportedZoomRatio(maxZoom).build())
                    .build())
                scanner = decoder
                torch.isEnabled = cam.cameraInfo.hasFlashUnit()
                zoom.isEnabled = maxZoom > 1f
                cam.cameraInfo.torchState.observe(this) { state -> torchOn = state == 1; torch.text = if (torchOn) "关闭补光" else "打开补光" }
                cam.cameraInfo.zoomState.observe(this) { state -> zoom.text = if (state.zoomRatio > 1.1f) "还原缩放" else "放大" }
                cam.cameraInfo.cameraState.observe(this) { state -> if (state.error != null) status.text = "相机暂不可用，请关闭其他相机应用后返回重试" }
                preview.previewStreamState.observe(this) { state ->
                    if (state == PreviewView.StreamState.STREAMING) { status.text = ""; focus(preview.width / 2f, preview.height * .42f) }
                }
                analyzer.setAnalyzer(executor) { frame ->
                    val media = frame.image
                    if (media == null || finished.get() || isDestroyed) { frame.close(); return@setAnalyzer }
                    try {
                        // Full frame, no viewfinder crop or JPEG conversion; use the sensor's actual rotation.
                        val input = InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees)
                        decoder.process(input).addOnSuccessListener(main) { codes ->
                            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || isFinishing) return@addOnSuccessListener
                            val values = codes.mapNotNull { it.rawValue }.map(String::trim).filter(String::isNotEmpty).distinct()
                            val result = ScanPayload.choose(values)
                            if (result != null && finished.compareAndSet(false, true)) {
                                setResult(Activity.RESULT_OK, Intent().putExtra("SCAN_RESULT", result)); finish()
                            } else if (values.isNotEmpty()) status.text = if (values.size > 1) "请只对准一个连接二维码" else "请扫描电脑插件生成的连接二维码"
                        }.addOnFailureListener(main) { if (!isDestroyed) status.text = "识别暂时失败，请重新对准二维码" }
                            .addOnCompleteListener { frame.close() }
                    } catch (_: Exception) { frame.close() }
                }
            } catch (_: Exception) { status.text = "无法打开相机，请检查相机权限或返回后重试" }
        }, main)
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        finished.set(true)
        analysis?.clearAnalyzer()
        analysis?.let { provider?.unbind(it) }
        cameraPreview?.let { provider?.unbind(it) }
        scanner?.close(); executor.shutdown()
        super.onDestroy()
    }
}

/** The guide is visual only; recognition uses the whole camera image. */
private class ScanGuide(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = RectF()
    init { contentDescription = "请将二维码置于取景框内。" }
    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val available = (height - 200 * density).coerceAtLeast(40 * density)
        val side = minOf(width * .78f, height * .50f, available)
        val left = (width - side) / 2f; val top = 56 * density + (available - side) / 2f
        val bottom = top + side
        paint.color = 0x88000000.toInt(); paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), top, paint)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), paint)
        canvas.drawRect(0f, top, left, bottom, paint)
        canvas.drawRect(left + side, top, width.toFloat(), bottom, paint)
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density
        frame.set(left, top, left + side, bottom)
        canvas.drawRoundRect(frame, 12 * density, 12 * density, paint)
        paint.style = Paint.Style.FILL; paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics); paint.textAlign = Paint.Align.CENTER
        canvas.drawText("请将二维码置于取景框内。", width / 2f, bottom + 20 * density - paint.fontMetrics.ascent, paint)
    }
}
