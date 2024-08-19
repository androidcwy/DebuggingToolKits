package com.example.scantools.widget.camera.face

import com.example.scantools.widget.camera.face.tasks.DetectionTask
import com.google.mlkit.vision.face.Face

/**
 * @author JoeYe
 * @date 2024/1/8 14:46
 */
data class FaceLivenessProcessListener(
    val onTaskStarted: DetectionTask.() -> Unit,
    val onTaskInvoked: DetectionTask.(Face?) -> Unit,
    val onTaskCompleted: DetectionTask.() -> Unit,
    val onTaskError: DetectionTask?.(Int) -> Unit
)