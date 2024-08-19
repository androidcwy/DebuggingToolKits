package com.example.scantools.bindings

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.databinding.BindingAdapter
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.example.scantools.utils.findActivity
import com.example.scantools.utils.notNull
import com.example.scantools.widget.RoundPreviewView
import com.example.scantools.widget.camera.CameraScanLifecycle
import com.example.scantools.widget.camera.FaceDetectionAnalyzer
import com.example.scantools.widget.camera.GreeCameraConfigFactory
import com.example.scantools.widget.camera.face.FaceDetectionConfig
import com.example.scantools.widget.camera.face.FaceLivenessAnalyzeHandler
import com.example.scantools.widget.camera.face.FaceLivenessProcessListener
import com.example.scantools.widget.camera.scan.GreeBarcodeScanningAnalyzer
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.face.Face
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan.OnScanResultCallback

/**
 * @author JoeYe
 * @date 2023/8/29 10:45
 */

interface PreviewControllerInterface {
    fun startCameraScan()

    fun toggleFlash(): Boolean

    fun setAnalyzeImage(state: Boolean)

    fun capture(result: ImageProxy?.() -> Unit)

    fun reset()

    fun averageFaceFrameInterval(): Long

    fun getPreviewView(): PreviewView?

    fun getErrorState(): Int?

    fun trySwitchNextTask()

    fun zoomTo(ratio: Float)
}

open class CodeScanListener(
    val result: AnalyzeResult<List<Barcode>>?.() -> Unit,
    val interceptor: (AnalyzeResult<List<Barcode>>.() -> AnalyzeResult<List<Barcode>>?)? = null
)

open class FaceScanListener(
    val result: AnalyzeResult<List<Face>>?.() -> Unit,
    val failure: (() -> Unit)? = null
)

class PreviewController : PreviewControllerInterface {
    var realSubject: PreviewControllerInterface? = null
    override fun startCameraScan() {
        realSubject?.startCameraScan()
    }

    override fun toggleFlash(): Boolean {
        return realSubject?.toggleFlash() ?: false
    }

    override fun setAnalyzeImage(state: Boolean) {
        realSubject?.setAnalyzeImage(state)
    }

    override fun capture(result: ImageProxy?.() -> Unit) {
        realSubject?.capture(result)
    }

    override fun reset() {
        realSubject?.reset()
    }

    override fun averageFaceFrameInterval(): Long {
        return realSubject?.averageFaceFrameInterval() ?: 0
    }

    override fun getPreviewView(): PreviewView? {
        return realSubject?.getPreviewView()
    }

    override fun getErrorState(): Int? {
        return realSubject?.getErrorState()
    }

    override fun trySwitchNextTask() {
        realSubject?.trySwitchNextTask()
    }

    override fun zoomTo(ratio: Float) {
        realSubject?.zoomTo(ratio)
    }
}

@BindingAdapter("previewController", "codeListener", requireAll = false)
fun bindPreview(
    cameraView: PreviewView,
    previewController: PreviewController?,
    codeListener: CodeScanListener?
) {
    if (previewController != null && codeListener != null) {
        val activity = cameraView.context.findActivity() as? ComponentActivity
        val lifecycleOwner = cameraView.findViewTreeLifecycleOwner()
        notNull(activity, lifecycleOwner) {
            val realCameraScan =
                CameraScanLifecycle<List<Barcode>>(activity!!, cameraView).life(lifecycleOwner!!)
                    .analyzer(GreeBarcodeScanningAnalyzer(Barcode.FORMAT_ALL_FORMATS,
                        emptyList<Int>().toIntArray(), null
                    ) { p0 ->
                        previewController.zoomTo(ratio = p0)
                        true
                    })
                    .callback {
                        codeListener.result.invoke(it)
                    }.apply {
                        setCameraConfig(
                            GreeCameraConfigFactory.createDefaultCameraConfig(
                                activity,
                                CameraSelector.LENS_FACING_BACK
                            )
                        )
                        resultInterceptor = codeListener.interceptor
                    }

            previewController.apply {
                realSubject = object : PreviewControllerInterface {
                    override fun startCameraScan() {
                        realCameraScan.startCamera()
                    }

                    override fun toggleFlash(): Boolean {
                        val result = !realCameraScan.isTorchEnabled
                        realCameraScan.enableTorch(result)
                        return result
                    }

                    override fun setAnalyzeImage(state: Boolean) {
                        realCameraScan.setAnalyzeImage(state)
                    }

                    override fun capture(result: ImageProxy?.() -> Unit) {
                        realCameraScan.capture(result)
                    }

                    override fun reset() {}

                    override fun averageFaceFrameInterval(): Long {
                        return 0L
                    }

                    override fun getPreviewView(): PreviewView? {
                        return cameraView
                    }

                    override fun getErrorState(): Int? {
                        return null
                    }

                    override fun trySwitchNextTask() {}

                    override fun zoomTo(ratio: Float) {
                        realCameraScan.zoomTo(ratio)
                    }

                }
            }

            realCameraScan.setAnalyzeImage(true)
            realCameraScan.startCamera()
        }
    }
}

@BindingAdapter("title", requireAll = false)
fun bindRoundPreviewViewTitle(
    cameraView: RoundPreviewView,
    title: String?
) {
    cameraView.setTitle(title)
}

@BindingAdapter("previewController", "faceListener",
    "faceLivenessProcessListener", "startFaceDetection", requireAll = false)
fun bindRoundPreviewView(
    cameraView: RoundPreviewView,
    previewController: PreviewController?,
    faceListener: FaceScanListener?,
    faceLivenessProcessListener: FaceLivenessProcessListener?,
    startFaceDetection: Boolean?
) {
    bindPreview(cameraView.previewView, previewController, faceListener, faceLivenessProcessListener, startFaceDetection)
}

@BindingAdapter("previewController", "faceListener",
    "faceLivenessProcessListener", "startFaceDetection", requireAll = false)
fun bindPreview(
    cameraView: PreviewView,
    previewController: PreviewController?,
    faceListener: FaceScanListener?,
    faceLivenessProcessListener: FaceLivenessProcessListener?,
    startFaceDetection: Boolean?
) {
    if (previewController != null && faceListener != null && startFaceDetection == true) {
        val activity = cameraView.context as? ComponentActivity
        val lifecycleOwner = cameraView.findViewTreeLifecycleOwner()

        notNull(activity, lifecycleOwner) {
            val realCameraScan = CameraScanLifecycle<List<Face>>(activity!!, cameraView).life(lifecycleOwner!!)
                .analyzer(FaceDetectionAnalyzer())
                .callback(object : OnScanResultCallback<List<Face>> {
                    override fun onScanResultCallback(result: AnalyzeResult<List<Face>>) {
                        faceListener.result.invoke(result)
                    }

                    override fun onScanResultFailure() {
                        super.onScanResultFailure()
                        faceListener.failure?.invoke()
                    }

                }).apply {
                    setCameraConfig(GreeCameraConfigFactory.createDefaultCameraConfig(activity,  CameraSelector.LENS_FACING_FRONT))
                }

            val handler = FaceLivenessAnalyzeHandler(
                realCameraScan.mOnAnalyzeListener!!, faceLivenessProcessListener!!
            )

            cameraView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val previewViewWidth = cameraView.width
                val previewViewHeight = cameraView.height
                if (previewViewWidth != 0 && previewViewHeight != 0) {
                    handler.faceDetectionConfig = FaceDetectionConfig(
                        previewViewWidth, previewViewHeight
                    )
                }
            }
            notNull(realCameraScan.mOnAnalyzeListener, faceLivenessProcessListener) {
                realCameraScan.analyzeListener(handler)
            }

            previewController.apply {
                realSubject = object : PreviewControllerInterface {
                    override fun startCameraScan() {
                        realCameraScan.startCamera()
                    }

                    override fun toggleFlash(): Boolean {
                        val result = !realCameraScan.isTorchEnabled
                        realCameraScan.enableTorch(result)
                        return result
                    }

                    override fun setAnalyzeImage(state: Boolean) {
                        realCameraScan.setAnalyzeImage(state)
                    }

                    override fun capture(result: ImageProxy?.() -> Unit) {
                        realCameraScan.capture(result)
                    }

                    override fun reset() {
                        val listener = realCameraScan.mOnAnalyzeListener
                        if (listener is FaceLivenessAnalyzeHandler) {
                            listener.reset()
                        }
                    }

                    override fun averageFaceFrameInterval(): Long {
                        return handler.getAverageFaceFrameInterval()
                    }

                    override fun getPreviewView(): PreviewView? {
                        return cameraView
                    }

                    override fun getErrorState(): Int? {
                        return handler.currentErrorState
                    }

                    override fun trySwitchNextTask() {
                        handler.trySwitchNextTask()
                    }

                    override fun zoomTo(ratio: Float) {

                    }

                }
            }

            realCameraScan.setAnalyzeImage(true)
            realCameraScan.startCamera()
        }
    }
}