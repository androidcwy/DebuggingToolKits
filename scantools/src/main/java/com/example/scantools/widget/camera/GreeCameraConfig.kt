package com.example.scantools.widget.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import com.king.camera.scan.CameraScan
import com.king.camera.scan.config.CameraConfig
import com.king.camera.scan.util.LogUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * @author JoeYe
 * @date 2024/6/19 11:13
 */
open class GreeCameraConfig(context: Context, imageQuality: Int) :
    CameraConfig() {
    private var mTargetSize: Size? = null

    init {

        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        LogUtils.d(String.format(Locale.getDefault(), "displayMetrics: %dx%d", width, height))


        // 因为为了保持流畅性和性能，尽可能的限制在imageQuality（默认：1080p），在此前提下尽可能的找到屏幕接近的分辨率
        if (width < height) {
            val ratio = height / width.toFloat()
            val size = min(width.toDouble(), imageQuality.toDouble()).toInt()
            mTargetSize =
                if (abs((ratio - CameraScan.ASPECT_RATIO_4_3).toDouble()) < abs((ratio - CameraScan.ASPECT_RATIO_16_9).toDouble())) {
                    Size(size, Math.round(size * CameraScan.ASPECT_RATIO_4_3))
                } else {
                    Size(size, Math.round(size * CameraScan.ASPECT_RATIO_16_9))
                }
        } else {
            val size = min(height.toDouble(), imageQuality.toDouble()).toInt()
            val ratio = width / height.toFloat()
            mTargetSize =
                if (abs((ratio - CameraScan.ASPECT_RATIO_4_3).toDouble()) < abs((ratio - CameraScan.ASPECT_RATIO_16_9).toDouble())) {
                    Size(Math.round(size * CameraScan.ASPECT_RATIO_4_3), size)
                } else {
                    Size(Math.round(size * CameraScan.ASPECT_RATIO_16_9), size)
                }
        }
        LogUtils.d("targetSize: $mTargetSize")
    }

    override fun options(builder: Preview.Builder): Preview {
        builder.setTargetResolution(mTargetSize!!)
        return super.options(builder)
    }

    open fun options(builder: ImageCapture.Builder): ImageCapture {
        mTargetSize?.let { builder.setTargetResolution(it) }
        return builder.build()
    }

    override fun options(builder: ImageAnalysis.Builder): ImageAnalysis {
        builder.setTargetResolution(mTargetSize!!)
        return super.options(builder)
    }
}