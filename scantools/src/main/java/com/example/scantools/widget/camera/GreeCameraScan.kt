package com.example.scantools.widget.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import androidx.annotation.FloatRange
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.google.common.util.concurrent.ListenableFuture
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.king.camera.scan.analyze.Analyzer
import com.king.camera.scan.analyze.Analyzer.OnAnalyzeListener
import com.king.camera.scan.config.CameraConfig
import com.king.camera.scan.config.CameraConfigFactory
import com.king.camera.scan.manager.AmbientLightManager
import com.king.camera.scan.manager.BeepManager
import com.king.camera.scan.util.LogUtils
import java.util.concurrent.Executors


/**
 * @author JoeYe
 * @date 2024/1/4 08:48
 */
open class GreeCameraScan<T>(
    context: Context,
    lifecycleOwner: LifecycleOwner?,
    previewView: PreviewView?
) : CameraScan<T>() {

    /**
     * Defines the maximum duration in milliseconds between a touch pad
     * touch and release for a given touch to be considered a tap (click) as
     * opposed to a hover movement gesture.
     */
    private val HOVER_TAP_TIMEOUT = 150

    /**
     * Defines the maximum distance in pixels that a touch pad touch can move
     * before being released for it to be considered a tap (click) as opposed
     * to a hover movement gesture.
     */
    private val HOVER_TAP_SLOP = 20

    /**
     * 每次缩放改变的步长
     */
    private val ZOOM_STEP_SIZE = 0.1f

    private var mContext: Context? = context
    private var mLifecycleOwner: LifecycleOwner? = lifecycleOwner

    /**
     * 预览视图
     */
    private var mPreviewView: PreviewView? = previewView

    private var mCameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null

    /**
     * 相机
     */
    private var mCamera: Camera? = null

    /**
     * 图片拍摄
     */
    private var capture: ImageCapture? = null

    /**
     * 相机配置
     */
    private var mCameraConfig: CameraConfig? = null

    /**
     * 分析器
     */
    private var mAnalyzer: Analyzer<T>? = null

    /**
     * 是否分析
     */
    @Volatile
    private var isAnalyze = true

    /**
     * 是否已经分析出结果
     */
    @Volatile
    private var isAnalyzeResult = false

    /**
     * 闪光灯（手电筒）视图
     */
    private var flashlightView: View? = null

    /**
     * 分析结果
     */
    private var mResultLiveData: MutableLiveData<AnalyzeResult<T>?>? = null

    /**
     * 扫描结果回调
     */
    private var mOnScanResultCallback: OnScanResultCallback<T>? = null

    /**
     * 分析监听器
     */
    open var mOnAnalyzeListener: OnAnalyzeListener<AnalyzeResult<T>>? = null

    /**
     * 蜂鸣音效管理器：主要用于播放蜂鸣提示音和振动效果
     */
    private var mBeepManager: BeepManager? = null

    /**
     * 环境光线管理器：主要通过传感器来监听光线的亮度变化
     */
    private var mAmbientLightManager: AmbientLightManager? = null

    /**
     * 最后点击时间，根据两次点击时间间隔用于区分单机和触摸缩放事件
     */
    private var mLastHoveTapTime: Long = 0

    /**
     * 是否是点击事件
     */
    private var isClickTap = false

    /**
     * 按下时X坐标
     */
    private var mDownX = 0f

    /**
     * 按下时Y坐标
     */
    private var mDownY = 0f

    var resultInterceptor: (AnalyzeResult<T>.() -> AnalyzeResult<T>?)? = null

    /**
     * 缩放手势检测
     */
    private val mOnScaleGestureListener: OnScaleGestureListener =
        object : SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                if (mCamera != null) {
                    val ratio = mCamera!!.cameraInfo.zoomState.value!!.zoomRatio
                    // 根据缩放的手势和当前比例进行缩放
                    zoomTo(ratio * scale)
                    return true
                }
                return false
            }
        }

    /**
     * 初始化
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initData() {
        mResultLiveData = MutableLiveData<AnalyzeResult<T>?>()
        mResultLiveData!!.observe(
            mLifecycleOwner!!
        ) { result: AnalyzeResult<T>? ->
            if (result != null) {
                handleAnalyzeResult(result)
            } else if (mOnScanResultCallback != null) {
                mOnScanResultCallback!!.onScanResultFailure()
            }
        }
        mOnAnalyzeListener = object : OnAnalyzeListener<AnalyzeResult<T>> {
            override fun onSuccess(result: AnalyzeResult<T>) {
                mResultLiveData!!.postValue(result)
            }

            override fun onFailure(e: Exception?) {
                mResultLiveData!!.postValue(null)
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(mContext!!, mOnScaleGestureListener)
        mPreviewView!!.setOnTouchListener { v: View?, event: MotionEvent ->
            handlePreviewViewClickTap(event)
            if (isNeedTouchZoom()) {
                return@setOnTouchListener scaleGestureDetector.onTouchEvent(event)
            }
            false
        }
        mBeepManager = BeepManager(mContext)
        mAmbientLightManager = AmbientLightManager(mContext)
        if (mAmbientLightManager != null) {
            mAmbientLightManager!!.register()
            mAmbientLightManager!!.setOnLightSensorEventListener { dark: Boolean, lightLux: Float ->
                if (flashlightView != null) {
                    if (dark) {
                        if (flashlightView!!.visibility != View.VISIBLE) {
                            flashlightView!!.visibility = View.VISIBLE
                            flashlightView!!.isSelected = isTorchEnabled()
                        }
                    } else if (flashlightView!!.visibility == View.VISIBLE && !isTorchEnabled()) {
                        flashlightView!!.visibility = View.INVISIBLE
                        flashlightView!!.isSelected = false
                    }
                }
            }
        }
    }

    /**
     * 处理预览视图点击事件；如果触发的点击事件被判定对焦操作，则开始自动对焦
     *
     * @param event 事件
     */
    private fun handlePreviewViewClickTap(event: MotionEvent) {
        if (event.pointerCount == 1) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isClickTap = true
                    mDownX = event.x
                    mDownY = event.y
                    mLastHoveTapTime = System.currentTimeMillis()
                }

                MotionEvent.ACTION_MOVE -> isClickTap =
                    distance(mDownX, mDownY, event.x, event.y) < HOVER_TAP_SLOP

                MotionEvent.ACTION_UP -> if (isClickTap && mLastHoveTapTime + HOVER_TAP_TIMEOUT > System.currentTimeMillis()) {
                    // 开始对焦和测光
                    startFocusAndMetering(event.x, event.y)
                }
            }
        }
    }

    /**
     * 计算两点的距离
     *
     * @param aX a点X坐标
     * @param aY a点Y坐标
     * @param bX b点X坐标
     * @param bY b点Y坐标
     * @return
     */
    private fun distance(aX: Float, aY: Float, bX: Float, bY: Float): Float {
        val xDiff = aX - bX
        val yDiff = aY - bY
        return Math.sqrt((xDiff * xDiff + yDiff * yDiff).toDouble()).toFloat()
    }

    /**
     * 开始对焦和测光
     *
     * @param x X轴坐标
     * @param y Y轴坐标
     */
    private fun startFocusAndMetering(x: Float, y: Float) {
        if (mCamera != null) {
            val point = mPreviewView!!.meteringPointFactory.createPoint(x, y)
            val focusMeteringAction = FocusMeteringAction.Builder(point).build()
            if (mCamera!!.cameraInfo.isFocusMeteringSupported(focusMeteringAction)) {
                mCamera!!.cameraControl.startFocusAndMetering(focusMeteringAction)
                LogUtils.d("startFocusAndMetering: $x,$y")
            }
        }
    }

    override fun setCameraConfig(cameraConfig: CameraConfig?): CameraScan<T>? {
        if (cameraConfig != null) {
            mCameraConfig = cameraConfig
        }
        return this
    }

    @OptIn(ExperimentalCamera2Interop::class)
    override fun startCamera() {
        if (mCameraConfig == null) {
            mCameraConfig = CameraConfigFactory.createDefaultCameraConfig(mContext, -1)
        }
        LogUtils.d("CameraConfig: " + mCameraConfig!!.javaClass.simpleName)
        mCameraProviderFuture = ProcessCameraProvider.getInstance(mContext!!)
        mCameraProviderFuture!!.addListener({
            try {
                // 相机选择器
                val cameraSelector = mCameraConfig!!.options(CameraSelector.Builder())
                // 预览
                val preview =
                    mCameraConfig!!.options(Preview.Builder())
                // 设置SurfaceProvider
                preview.setSurfaceProvider(mPreviewView!!.surfaceProvider)
                // 图像分析
                val imageAnalysis = mCameraConfig!!.options(
                    ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                )
                imageAnalysis.setAnalyzer(
                    Executors.newFixedThreadPool(3)
                ) { image: ImageProxy ->
                    if (isAnalyze && !isAnalyzeResult && mAnalyzer != null) {
                        mAnalyzer!!.analyze(image, mOnAnalyzeListener!!)
                    }
                    image.close()
                }
                if (mCamera != null) {
                    mCameraProviderFuture!!.get().unbindAll()
                }
                capture = if (mCameraConfig is GreeCameraConfig) {
                    (mCameraConfig as GreeCameraConfig).options(ImageCapture.Builder())
                } else {
                    ImageCapture.Builder().build()
                }
                //绑定到生命周期
                mCamera = mCameraProviderFuture!!.get()
                    .bindToLifecycle(mLifecycleOwner!!, cameraSelector, capture, preview, imageAnalysis)

                mCamera?.cameraInfo?.zoomState?.value
            } catch (e: Exception) {
                LogUtils.e(e)
            }
        }, ContextCompat.getMainExecutor(mContext!!))
    }

    /**
     * 处理分析结果
     *
     * @param result 分析结果
     */
    @Synchronized
    private fun handleAnalyzeResult(result: AnalyzeResult<T>) {
        if (isAnalyzeResult || !isAnalyze) {
            return
        }
        val realResult = if (resultInterceptor != null) {
            resultInterceptor!!.invoke(result)
        } else {
            result
        }
        if (realResult == null) {
            return
        }

        isAnalyzeResult = true
        if (mBeepManager != null) {
            mBeepManager!!.playBeepSoundAndVibrate()
        }
        if (mOnScanResultCallback != null) {
            mOnScanResultCallback!!.onScanResultCallback(realResult)
        }
        isAnalyzeResult = false
    }

    override fun stopCamera() {
        if (mCameraProviderFuture != null) {
            try {
                mCameraProviderFuture!!.get().unbindAll()
            } catch (e: Exception) {
                LogUtils.e(e)
            }
        }
    }

    override fun setAnalyzeImage(analyze: Boolean): CameraScan<T>? {
        isAnalyze = analyze
        return this
    }

    override fun setAnalyzer(analyzer: Analyzer<T>?): CameraScan<T>? {
        mAnalyzer = analyzer
        return this
    }

    override fun zoomIn() {
        if (mCamera != null) {
            val ratio = getCameraInfo().zoomState.value!!.zoomRatio + ZOOM_STEP_SIZE
            val maxRatio = getCameraInfo().zoomState.value!!.maxZoomRatio
            if (ratio <= maxRatio) {
                mCamera!!.cameraControl.setZoomRatio(ratio)
            }
        }
    }

    override fun zoomOut() {
        if (mCamera != null) {
            val ratio = getCameraInfo().zoomState.value!!.zoomRatio - ZOOM_STEP_SIZE
            val minRatio = getCameraInfo().zoomState.value!!.minZoomRatio
            if (ratio >= minRatio) {
                mCamera!!.cameraControl.setZoomRatio(ratio)
            }
        }
    }

    override fun zoomTo(ratio: Float) {
        if (mCamera != null) {
            val zoomState = getCameraInfo().zoomState.value
            val maxRatio = zoomState!!.maxZoomRatio
            val minRatio = zoomState.minZoomRatio
            val zoom = Math.max(Math.min(ratio, maxRatio), minRatio)
            mCamera!!.cameraControl.setZoomRatio(zoom)
        }
    }

    override fun lineZoomIn() {
        if (mCamera != null) {
            val zoom = getCameraInfo().zoomState.value!!.linearZoom + ZOOM_STEP_SIZE
            if (zoom <= 1f) {
                mCamera!!.cameraControl.setLinearZoom(zoom)
            }
        }
    }

    override fun lineZoomOut() {
        if (mCamera != null) {
            val zoom = getCameraInfo().zoomState.value!!.linearZoom - ZOOM_STEP_SIZE
            if (zoom >= 0f) {
                mCamera!!.cameraControl.setLinearZoom(zoom)
            }
        }
    }

    override fun lineZoomTo(@FloatRange(from = 0.0, to = 1.0) linearZoom: Float) {
        if (mCamera != null) {
            mCamera!!.cameraControl.setLinearZoom(linearZoom)
        }
    }

    override fun enableTorch(torch: Boolean) {
        if (mCamera != null && hasFlashUnit()) {
            mCamera!!.cameraControl.enableTorch(torch)
        }
    }

    override fun isTorchEnabled(): Boolean {
        return if (mCamera != null) {
            getCameraInfo().torchState.value == TorchState.ON
        } else false
    }

    override fun hasFlashUnit(): Boolean {
        return if (mCamera != null) {
            getCameraInfo().hasFlashUnit()
        } else mContext!!.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    override fun setVibrate(vibrate: Boolean): CameraScan<T>? {
        if (mBeepManager != null) {
            mBeepManager!!.setVibrate(vibrate)
        }
        return this
    }

    override fun setPlayBeep(playBeep: Boolean): CameraScan<T>? {
        if (mBeepManager != null) {
            mBeepManager!!.setPlayBeep(playBeep)
        }
        return this
    }

    override fun setOnScanResultCallback(callback: OnScanResultCallback<T>?): CameraScan<T> {
        mOnScanResultCallback = callback
        return this
    }

    override fun getCamera(): Camera? {
        return mCamera
    }

    /**
     * CameraInfo
     *
     * @return [CameraInfo]
     */
    private fun getCameraInfo(): CameraInfo {
        return mCamera!!.cameraInfo
    }

    override fun release() {
        isAnalyze = false
        flashlightView = null
        if (mAmbientLightManager != null) {
            mAmbientLightManager!!.unregister()
        }
        if (mBeepManager != null) {
            mBeepManager!!.close()
        }
        stopCamera()
    }

    override fun bindFlashlightView(flashlightView: View?): CameraScan<T>? {
        this.flashlightView = flashlightView
        if (mAmbientLightManager != null) {
            mAmbientLightManager!!.isLightSensorEnabled = flashlightView != null
        }
        return this
    }

    override fun setDarkLightLux(lightLux: Float): CameraScan<T>? {
        if (mAmbientLightManager != null) {
            mAmbientLightManager!!.setDarkLightLux(lightLux)
        }
        return this
    }

    override fun setBrightLightLux(lightLux: Float): CameraScan<T>? {
        if (mAmbientLightManager != null) {
            mAmbientLightManager!!.setBrightLightLux(lightLux)
        }
        return this
    }

    init {
        initData()
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun capture(result: ImageProxy?.() -> Unit) {
        capture?.takePicture(ContextCompat.getMainExecutor(this@GreeCameraScan.mContext!!), object : OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                result.invoke(image)
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
            }
        })
    }

}