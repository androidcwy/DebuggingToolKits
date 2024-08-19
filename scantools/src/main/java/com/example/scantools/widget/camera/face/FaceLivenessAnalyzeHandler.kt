package com.example.scantools.widget.camera.face

import android.os.Build
import android.util.Log
import androidx.core.os.HandlerCompat
import com.example.scantools.utils.DetectionUtils
import com.example.scantools.utils.HandlerUtils
import com.example.scantools.widget.camera.face.tasks.DetectionTask
import com.example.scantools.widget.camera.face.tasks.EyesBlinkingDetectionTask
import com.example.scantools.widget.camera.face.tasks.FacingDetectionTask
import com.example.scantools.widget.camera.face.tasks.MouthOpenDetectionTask
import com.example.scantools.widget.camera.face.tasks.ShakeDetectionTask
import com.google.mlkit.vision.face.Face
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.analyze.Analyzer
import java.util.Deque
import java.util.LinkedList

/**
 * @author JoeYe
 * @date 2024/1/8 14:31
 */
class FaceLivenessAnalyzeHandler(
    private val impl: Analyzer.OnAnalyzeListener<AnalyzeResult<List<Face>>>,
    private val processListener: FaceLivenessProcessListener
) : Analyzer.OnAnalyzeListener<AnalyzeResult<List<Face>>> by impl {

    var faceDetectionConfig: FaceDetectionConfig? = null

    private val resetNoFaceTask = Runnable {
        changeErrorState(null, ERROR_NO_FACE)
        reset()
    }

    companion object {
        private const val FACE_CACHE_SIZE = 5
        private const val NO_ERROR = -1
        const val ERROR_NO_FACE = 0
        const val ERROR_MULTI_FACES = 1
        const val ERROR_OUT_OF_DETECTION_RECT = 2
        const val ERROR_DETECTION_TOO_FAR = 3
        const val ERROR_DETECTION_TOO_CLOSE = 4
    }

    // 目前仅保留检测人脸、张嘴的逻辑
    private val livenessTasks = arrayListOf(
        MouthOpenDetectionTask(),
//        EyesBlinkingDetectionTask()
        ShakeDetectionTask()
    )

    private val realLivenessTasks = arrayListOf<DetectionTask>()

    private fun executeRandomTask() {
        if (livenessTasks.size < 2) {
            throw IllegalStateException("Not enough tasks in the list")
        }

        realLivenessTasks.clear()

        realLivenessTasks.add(FacingDetectionTask())
        val mutableListTask = livenessTasks.toMutableList()
        mutableListTask.shuffle()
        realLivenessTasks.addAll(mutableListTask)
    }

    init {
        executeRandomTask()
    }

    private var taskIndex = 0
    private var lastTaskIndex = -1
    var currentErrorState = NO_ERROR
    private val lastFaces: Deque<Face> = LinkedList()
    // 用于记录当前图像是否已经通过活体校验 true:已通过 false:未通过或重置
    private var faceAuthSuccessful: Boolean = false

    override fun onSuccess(result: AnalyzeResult<List<Face>>) {
        countTime()

        // 判断已经通过活体 把后续的图像都直接返回外部 给炫彩识别用
        // 但是图像如果人脸有误，会返回失败回调
        if (faceAuthSuccessful) {
            val face =
                filter(
                    null, result.result,
                    result.bitmap?.width ?: 640,
                    result.bitmap?.height ?: 640
                )
            if (face != null) {
                impl.onSuccess(result)
            } else {
                impl.onFailure(null)
            }
            return
        }

        val task = realLivenessTasks.getOrNull(taskIndex) ?: return
        if (taskIndex != lastTaskIndex) {
            lastTaskIndex = taskIndex
            task.start()
            Log.e("test0814111", "onTaskStarted: $task")
            processListener.onTaskStarted.invoke(task)
        }
        val face =
            filter(
                task, result.result,
                result.bitmap?.width ?: 640,
                result.bitmap?.height ?: 640
            )

        Log.e("test0814111", "onTaskInvoked: $task")
        processListener.onTaskInvoked.invoke(task, face)

        if (face != null) {
            HandlerUtils.removeRunnable(resetNoFaceTask)

            if (task.process(face)) {
                Log.e("test0814111", "onTaskCompleted: $task")
                processListener.onTaskCompleted.invoke(task)
                val processedTaskIndex = taskIndex
                taskIndex++
                // 检测成功 跳出判定循环
                // 目前只有一个动作 但是队列其实存了多于 1 个动作 最终执行的动作是随机的 所以通过了其中一个就认为全部通过
                if (processedTaskIndex > 0 || taskIndex == realLivenessTasks.size) {
//                if (taskIndex == realLivenessTasks.size) {
                    faceAuthSuccessful = true
                    impl.onSuccess(result)
                }
            }
        }
    }

    override fun onFailure(e: Exception?) {
        // 如果已经检测成功过 但是未 reset 会忽略失败情况
        // 因为图像还会抛出外部给炫彩用
        countTime()

        Log.e("test0818", "on failure: ")
        impl.onFailure(e)

        // 需求:如果已经进入"炫彩"模式，不需要重置内部的人脸检测 task 列表
        if (!faceAuthSuccessful) {
            // 可能存在做动作时检测不到人脸的情况 给个两秒时间做缓冲
            // 如果两秒内有继续人脸操作 则判定可以继续识别流程 否则得重置
            val hasCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                HandlerUtils.getMainHandler().hasCallbacks(resetNoFaceTask)
            } else {
                HandlerCompat.hasCallbacks(HandlerUtils.getMainHandler(), resetNoFaceTask)
            }

            if (!hasCallback) {
                HandlerUtils.postRunnable(resetNoFaceTask, 1000L)
            }
        }
    }

    fun reset() {
        taskIndex = 0
        lastTaskIndex = -1
        faceAuthSuccessful = false
        lastFaces.clear()
    }

    private var lastFaceDetectedTimestamp = 0L
    private val faceFrameIntervals = mutableListOf<Long>()
    private val maxSamples = 10 // Adjust as needed

    private fun countTime(){
        val currentTimestamp = System.currentTimeMillis()
        if (lastFaceDetectedTimestamp != 0L) {
            val interval = currentTimestamp - lastFaceDetectedTimestamp
            faceFrameIntervals.add(interval)
            if (faceFrameIntervals.size > maxSamples) {
                faceFrameIntervals.removeFirst()
            }
        }
        lastFaceDetectedTimestamp = currentTimestamp
    }

    fun getAverageFaceFrameInterval(): Long {
        return if (faceFrameIntervals.isNotEmpty()) {
            faceFrameIntervals.average().toLong()
        } else {
            0L // Or handle the case where no faces have been detected yet
        }
    }

    // 更新执行下一个动作，排除第一个动作和最后一个动作
    fun trySwitchNextTask(){
        if (taskIndex > 0 && taskIndex < realLivenessTasks.size - 1){
            taskIndex ++
        }
    }

    /**
     * 过滤异常人脸情况
     * tips:当已经通过人脸并且给到外部"炫彩"识别使用时，证明已经到了第二步，不需要重置当前task 列表
     */
    private fun filter(task: DetectionTask?, faces: List<Face>?, detectionWidthSize: Int,
                       detectionHeightSize: Int): Face? {
        if (faces != null && faces.size > 1) {
            changeErrorState(task, ERROR_MULTI_FACES)
            if (!faceAuthSuccessful) {
                reset()
            }
            return null
        }

        if (faces.isNullOrEmpty() && lastFaces.isEmpty()) {
            changeErrorState(task, ERROR_NO_FACE)
            if (!faceAuthSuccessful) {
                reset()
            }
            return null
        }

        val face = faces?.firstOrNull() ?: lastFaces.pollFirst()
        if (!DetectionUtils.isFaceInDetectionRect(
                face,
                faceDetectionConfig?.faceAreaDx ?: 0,
                faceDetectionConfig?.faceAreaDy ?: 0,
                detectionWidthSize,
                detectionHeightSize
            )
        ) {
            changeErrorState(task, ERROR_OUT_OF_DETECTION_RECT)
            if (!faceAuthSuccessful) {
                reset()
            }
            return null
        }

        faceDetectionConfig?.let {
            if (it.faceAreaWidth != 0 && it.faceAreaHeight != 0){
                if (DetectionUtils.isFaceTooFar(
                        face,
                        it.faceAreaHeight
                    )
                ) {
                    changeErrorState(task, ERROR_DETECTION_TOO_FAR)
                    return null
                }

                if (DetectionUtils.isFaceTooClose(
                        face,
                        it.faceAreaHeight
                    )
                ) {
                    changeErrorState(task, ERROR_DETECTION_TOO_CLOSE)
                    return null
                }
            }
        }

        if (!faces.isNullOrEmpty()) {
            // cache current face
            lastFaces.offerFirst(face)
            if (lastFaces.size > FACE_CACHE_SIZE) {
                lastFaces.pollLast()
            }
        }
        changeErrorState(task, NO_ERROR)
        return face
    }

    private fun changeErrorState(task: DetectionTask?, newErrorState: Int) {
        if (newErrorState != currentErrorState) {
            currentErrorState = newErrorState
            if (currentErrorState != NO_ERROR) {
                processListener.onTaskError.invoke(task, newErrorState)
            }
        }
    }

}