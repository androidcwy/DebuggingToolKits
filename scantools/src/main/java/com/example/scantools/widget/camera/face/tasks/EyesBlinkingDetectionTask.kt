package com.example.scantools.widget.camera.face.tasks

import com.example.scantools.utils.DetectionUtils
import com.google.mlkit.vision.face.Face

class EyesBlinkingDetectionTask : DetectionTask {

    override fun process(face: Face): Boolean {
        val leftEyeOpenPoint = face.leftEyeOpenProbability
        val rightEyeOpenPoint = face.rightEyeOpenProbability
        if (leftEyeOpenPoint != null && rightEyeOpenPoint != null) {
            return DetectionUtils.isFacing(face) &&
                    (leftEyeOpenPoint < 0.4 || rightEyeOpenPoint < 0.4)
        }
        return false
    }
}