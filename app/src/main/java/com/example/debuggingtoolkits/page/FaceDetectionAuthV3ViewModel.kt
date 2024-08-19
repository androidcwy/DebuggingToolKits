package com.example.debuggingtoolkits.page

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.databinding.ObservableLong
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.debuggingtoolkits.R
import com.example.scantools.bindings.ClickListener
import com.example.scantools.bindings.FaceScanListener
import com.example.scantools.bindings.PreviewController
import com.example.scantools.utils.AppGlobal
import com.example.scantools.utils.getGlobalColor
import com.example.scantools.utils.getGlobalColorArray
import com.example.scantools.utils.getGlobalString
import com.example.scantools.utils.notNull
import com.example.scantools.widget.camera.face.FaceLivenessAnalyzeHandler.Companion.ERROR_DETECTION_TOO_CLOSE
import com.example.scantools.widget.camera.face.FaceLivenessAnalyzeHandler.Companion.ERROR_DETECTION_TOO_FAR
import com.example.scantools.widget.camera.face.FaceLivenessAnalyzeHandler.Companion.ERROR_MULTI_FACES
import com.example.scantools.widget.camera.face.FaceLivenessAnalyzeHandler.Companion.ERROR_NO_FACE
import com.example.scantools.widget.camera.face.FaceLivenessAnalyzeHandler.Companion.ERROR_OUT_OF_DETECTION_RECT
import com.example.scantools.widget.camera.face.FaceLivenessProcessListener
import com.example.scantools.widget.camera.face.tasks.DetectionTask
import com.example.scantools.widget.camera.face.tasks.EyesBlinkingDetectionTask
import com.example.scantools.widget.camera.face.tasks.FacingDetectionTask
import com.example.scantools.widget.camera.face.tasks.MouthOpenDetectionTask
import com.example.scantools.widget.camera.face.tasks.ShakeDetectionTask
import com.example.scantools.widget.camera.face.tasks.SmileDetectionTask
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @author JoeYe
 * @date 2023/9/6 16:20
 */
class FaceDetectionAuthV3ViewModel : ViewModel() {

    private val pupilLivenessColorGroup =
        getGlobalColorArray(R.array.account_face_pupil_liveness_group)

    val detectionBgColor = ObservableInt()
    val iconBack = ObservableInt()

    val backClick = ClickListener {
        // 退出页面
    }

    private var pupilLivenessDetectionJob: Job? = null
    private var pupilLivenessDetectionHandleFaceJob: Job? = null
    val countDownTimerTime = ObservableLong(3000L)
    val startFaceDetector = ObservableBoolean()
    val faceDetectorDynamicText = ObservableField("")
    val faceDetectorDynamicSimpleText = ObservableField("")

    val previewController = PreviewController()


    private var currentFaceBitmap: Bitmap? = null
    private var hasFace: Boolean = false

    private val lightEnable = ObservableBoolean(true)

    val faceDetectListener = FaceScanListener({
        notNull(this?.bitmap, this?.result) {
            // 环境光亮不允许时 不支持回调
            if (!lightEnable.get()) {
                return@FaceScanListener
            }

            hasFace = true
            currentFaceBitmap = this!!.bitmap

            if (!isPupilLivenessDetectionActive) {
                isPupilLivenessDetectionActive = true
                startPupilLivenessDetection()
            }
        }
    }) {
        // 环境光亮不允许时 不支持回调
        if (!lightEnable.get()) {
            return@FaceScanListener
        }
        handleErrorFaceWhenFailed()
    }

    /**
     * 各种异常状态时，重新恢复界面
     * 1.停止炫光
     * 2.修改样式
     * 3.判断是否需要重置识别流程
     */
    private fun handleErrorFaceWhenFailed(resetFaceDetection: Boolean = false, errTips: String? = null) {
        if (isPupilLivenessDetectionActive) {
            if (pupilLivenessDetectionJob?.isActive == true) {
                pupilLivenessDetectionJob?.cancel()
            }
            isPupilLivenessDetectionActive = false
            detectionBgColor.set(getGlobalColor(R.color.white))
        }

        if (pupilLivenessDetectionHandleFaceJob?.isActive == true) {
            pupilLivenessDetectionHandleFaceJob?.cancel()
        }

        if (switchTaskJob?.isActive == true) {
            switchTaskJob?.cancel()
        }

        val tips = errTips ?: getErrorTips(previewController.getErrorState())
            ?: getGlobalString(R.string.account_login_face_detection_str8)

        Log.e("test0818", "handle tips text:${tips}")

        faceDetectorDynamicText.set(tips)
        faceDetectorDynamicSimpleText.set(null)
        // 避免有地方暂停掉人脸帧获取，这里手动重启
        setAnalyzeImage(true)

        if (resetFaceDetection) {
            setAnalyzeImage(state = true, resetAll = true)
        }
    }

    @Synchronized
    private fun setAnalyzeImage(state: Boolean, resetAll: Boolean = false){
        previewController.setAnalyzeImage(state)
        if (resetAll) {
            previewController.reset()
        }
    }

    private var isPupilLivenessDetectionActive: Boolean = false

    private fun startPupilLivenessDetection() {
        pupilLivenessDetectionJob = viewModelScope.launch(Dispatchers.Default) {
            try {

                faceDetectorDynamicText.set(getGlobalString(R.string.account_face_auth_str11))
                faceDetectorDynamicSimpleText.set(null)

                val collectedFaceFrames = mutableListOf<String>()
                val colors = prepareColors()

                var currentColorIndex = 0

                while (isActive) {
                    colors.getOrNull(currentColorIndex)?.let { detectionBgColor.set(it) }

                    delay(
                        if (currentColorIndex == colors.size - 1) {
                            faceCollectIntervalWhite
                        } else faceCollectDelayTimeMills
                    )

                    collectFaceFramesAndUpload(
                        collectedFaceFrames,
                        colors.size
                    )

                    if (currentColorIndex == colors.size - 1) {
                        break
                    }
                    currentColorIndex =
                        (currentColorIndex + 1) % colors.size
                }

                setAnalyzeImage(false)
            } catch (_: CancellationException) {
                setAnalyzeImage(true)
            }
        }
    }

    private fun collectFaceFramesAndUpload(
        collectedFaceFrames: MutableList<String>,
        totalColors: Int
    ) {
        pupilLivenessDetectionHandleFaceJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                collectedFaceFrames.add(totalColors.toString())
                // 提交
                if (isActive && collectedFaceFrames.size == totalColors) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            AppGlobal.application,
                            "人脸炫光完成!!!", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (_: CancellationException) {
                setAnalyzeImage(true)
            }
        }
    }

    private fun prepareColors(): List<Int> {
        val colors = pupilLivenessColorGroup.toMutableList()
        colors.shuffle()
        colors.add(getGlobalColor(R.color.white))
        return colors
    }

    val faceLivenessProcessListener = FaceLivenessProcessListener(
        onTaskStarted = {
            handleTaskEvent(this)
        },

        onTaskInvoked = {face ->
            handleTaskEvent(this, face != null)
        },
        onTaskCompleted = {
//            when(this) {
//                is FacingDetectionTask -> faceDetectorDynamicText.set("找到人脸成功")
//                is MouthOpenDetectionTask -> faceDetectorDynamicText.set("张开嘴巴成功")
//                is ShakeDetectionTask -> faceDetectorDynamicText.set("左右摇头成功")
//                is SmileDetectionTask -> faceDetectorDynamicText.set("笑脸成功")
//
//            }

            if (this !is FacingDetectionTask && switchTaskJob?.isActive == true) {
                switchTaskJob?.cancel()
            }
        },
        onTaskError = { state ->
            // 环境光亮不允许时 不支持回调
            if (!lightEnable.get()) {
                return@FaceLivenessProcessListener
            }
            Log.e("test0818", "onTaskError text:${getErrorTips(state)}")

            faceDetectorDynamicText.set(getErrorTips(state))
            faceDetectorDynamicSimpleText.set(null)
        }
    )

    private fun handleTaskEvent(task: DetectionTask, detectSuccess: Boolean = true) {
        if (!lightEnable.get()) return

        if (detectSuccess) {
            val textResId = when (task) {
                is FacingDetectionTask -> R.string.account_login_face_detection_str2
                is MouthOpenDetectionTask -> R.string.account_login_face_detection_str3
                is ShakeDetectionTask -> R.string.account_login_face_detection_str4
                is SmileDetectionTask -> R.string.account_login_face_detection_str5
                else -> return
            }

            faceDetectorDynamicText.set(getGlobalString(textResId))
            faceDetectorDynamicSimpleText.set(
                when (task) {
                    is MouthOpenDetectionTask -> getGlobalString(R.string.account_face_auth_str5)
                    is ShakeDetectionTask -> getGlobalString(R.string.account_face_auth_str23)
                    else -> null
                }
            )

            if (task !is FacingDetectionTask && (switchTaskJob == null || switchTaskJob?.isActive == false)) {
                startSwitchTaskMonitoring()
            }
        } else {
            if (switchTaskJob?.isActive == true) {
                switchTaskJob?.cancel()
            }
        }
    }

    private fun getErrorTips(state: Int?): String?{
        return  when (state) {
            ERROR_NO_FACE -> {
                getGlobalString(R.string.account_login_face_detection_str2)
            }
            ERROR_MULTI_FACES -> {
                getGlobalString(R.string.account_login_face_detection_str7)
            }
            ERROR_OUT_OF_DETECTION_RECT -> {
                getGlobalString(R.string.account_login_face_detection_str8)
            }

            ERROR_DETECTION_TOO_FAR -> {
                "距离太远了"
            }

            ERROR_DETECTION_TOO_CLOSE -> {
                "距离太近了"
            }

            else -> null
        }
    }

    private var faceCollectDelayTimeMills = 300L
    private var faceCollectIntervalWhite = 600L

    fun init() {
        // 进入页面后，有 3 秒的准备提示
        startFaceDetector.set(true)
    }

    fun onDestroy() {
        if (pupilLivenessDetectionJob?.isActive == true) {
            pupilLivenessDetectionJob?.cancel()
        }
        if (pupilLivenessDetectionHandleFaceJob?.isActive == true) {
            pupilLivenessDetectionHandleFaceJob?.cancel()
        }
        if (switchTaskJob?.isActive == true) {
            switchTaskJob?.cancel()
        }
    }

    private var switchTaskJob: Job? = null

    private fun startSwitchTaskMonitoring() {
        if (switchTaskJob?.isActive == true) {
            switchTaskJob?.cancel()
        }
        switchTaskJob = viewModelScope.launch {
            try {
                delay(5000L)
                if (isActive) {
                    previewController.trySwitchNextTask()
                }
            } catch (_: CancellationException) {
            }
        }
    }

}