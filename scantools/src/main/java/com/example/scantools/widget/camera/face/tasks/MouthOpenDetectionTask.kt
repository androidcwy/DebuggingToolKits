package com.example.scantools.widget.camera.face.tasks

import com.example.scantools.utils.DetectionUtils
import com.google.mlkit.vision.face.Face

class MouthOpenDetectionTask : DetectionTask {

    private val detectionUtils = DetectionUtils()

    override fun process(face: Face): Boolean {
        return DetectionUtils.isFacing(face) && detectionUtils.isMouthOpened(face)
    }
}