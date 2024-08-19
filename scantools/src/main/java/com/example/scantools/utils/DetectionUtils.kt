package com.example.scantools.utils

import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import com.king.camera.scan.BuildConfig
import kotlin.math.acos
import kotlin.math.sqrt


class DetectionUtils {

    companion object {

        fun isFacing(face: Face): Boolean {
            return face.headEulerAngleZ < 7.78f && face.headEulerAngleZ > -7.78f
                    && face.headEulerAngleY < 11.8f && face.headEulerAngleY > -11.8f
                    && face.headEulerAngleX < 19.8f && face.headEulerAngleX > -19.8f
        }

        // 将检测区域抽象成 8x8 窗格计算
        // - 人脸中心点限定在中间的 4x4 方块中
        // - 人脸不能大于 6x6 且不能小于 3x3
        fun isFaceInDetectionRect(
            face: Face,
            dx: Int = 0,
            dy: Int = 0,
            detectionWidthSize: Int,
            detectionHeightSize: Int
        ): Boolean {
            val fRect = face.boundingBox
            val realFx = fRect.centerX() + dx
            val realFy = fRect.centerY() + dy
            val gridWidthSize = detectionWidthSize / 10
            val gridHeightSize = detectionHeightSize / 10

            Log.e(
                "test0809", "gridWidthSize： ${gridWidthSize} gridHeightSize:${gridHeightSize}" +
                        " fx:${realFx} fy:${realFy}"
            )

            if (realFx < gridWidthSize * 3 || realFx > gridWidthSize * 7 || realFy < gridHeightSize * 3 || realFy > gridHeightSize * 7) {
                Log.d(
                    "isFaceInDetectionRect", "face center point is out of rect: ($realFx, $realFy) " +
                            "aa:${realFx < gridWidthSize * 3} " +
                            "bb:${realFx > gridWidthSize * 8} " +
                            "cc:${realFy < gridHeightSize * 3} " +
                            "dd:${realFy > gridHeightSize * 8} " +
                            "" +
                            "gridWidthSize:$gridWidthSize " +
                            "gridHeightSize:$gridHeightSize "
                )
                return false
            }
            return true
        }

        fun isFaceTooClose(
            face: Face, baseHeight: Int, heightThreshold: Float = 0.15f
        ): Boolean {
            val faceWidth = face.boundingBox.width()
            val faceHeight = face.boundingBox.height()
            val isHeightTooClose =
                (faceHeight.toFloat() / baseHeight.toFloat()) - 1 > heightThreshold

            Log.e(
                "DetectionUtils",
                "isFaceTooClose:, baseHeight=$baseHeight, " +
                        "faceWidth=$faceWidth, faceHeight=$faceHeight," +
                        " isHeightTooClose=$isHeightTooClose," +
                        " heightThreshold=$heightThreshold"
            )

            return isHeightTooClose

        }

        fun isFaceTooFar(
            face: Face,
            baseHeight: Int, heightThreshold: Float = 0.3f
        ): Boolean {
            val faceWidth = face.boundingBox.width()
            val faceHeight = face.boundingBox.height()

            val isHeightTooFar =
                1f - (faceHeight.toFloat() / baseHeight.toFloat()) > heightThreshold

            if (BuildConfig.DEBUG) {
                Log.e(
                    "DetectionUtils",
                    "isFaceTooFar: baseHeight=$baseHeight, " +
                            "faceWidth=$faceWidth, faceHeight=$faceHeight, " +
                            " isHeightTooFar=$isHeightTooFar, " +
                            " heightThreshold=$heightThreshold"
                )
            }
            return isHeightTooFar
        }
    }


    private var isMouthInitiallyOpen = false

    private var previewGammaDeg = 0.0

    fun isMouthOpened(face: Face): Boolean {
        val left = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return false
        val right = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return false
        val bottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position ?: return false

        // Square of lengths be a2, b2, c2
        val a2 = lengthSquare(right, bottom)
        val b2 = lengthSquare(left, bottom)
        val c2 = lengthSquare(left, right)

        // length of sides be a, b, c
        val a = sqrt(a2)
        val b = sqrt(b2)

        // From Cosine law
        val gamma = acos((a2 + b2 - c2) / (2 * a * b))

        // Converting to degrees
        val gammaDeg = gamma * 180 / Math.PI

        if (previewGammaDeg == 0.0) {
            isMouthInitiallyOpen = gammaDeg < 115f
        }

        previewGammaDeg = gammaDeg

        val result = !isMouthInitiallyOpen && gammaDeg < 115f

        if (gammaDeg > 115f) {
            isMouthInitiallyOpen = false
        }

        return result
    }

    private fun lengthSquare(a: PointF, b: PointF): Float {
        val x = a.x - b.x
        val y = a.y - b.y
        return x * x + y * y
    }
}