package com.example.scantools.widget.camera.face.tasks

import com.example.scantools.utils.DetectionUtils
import com.google.mlkit.vision.face.Face

class FacingDetectionTask : DetectionTask {

    companion object {
        private const val FACING_CAMERA_KEEP_TIME = 1000L
    }

    private var startTime = 0L

    override fun start() {
        startTime = System.currentTimeMillis()
    }

    override fun process(face: Face): Boolean {
        if (!DetectionUtils.isFacing(face)) {
            startTime = System.currentTimeMillis()
            return false
        }
        return System.currentTimeMillis() - startTime >= FACING_CAMERA_KEEP_TIME
    }
}