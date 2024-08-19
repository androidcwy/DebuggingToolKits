package com.example.scantools.widget.camera

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.king.mlkit.vision.common.analyze.CommonAnalyzer
import java.util.concurrent.Executors


/**
 * @author JoeYe
 * @date 2024/1/8 13:38
 */
class FaceDetectionAnalyzer: CommonAnalyzer<List<Face>>() {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setExecutor(Executors.newFixedThreadPool(3))
            .setMinFaceSize(0.25f)
            .build()
    )

    override fun detectInImage(inputImage: InputImage): Task<List<Face>> {
        return detector.process(inputImage)
    }
}