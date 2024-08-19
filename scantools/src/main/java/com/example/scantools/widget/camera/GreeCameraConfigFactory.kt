package com.example.scantools.widget.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import com.king.camera.scan.config.AspectRatioCameraConfig
import com.king.camera.scan.config.CameraConfig
import com.king.camera.scan.config.ResolutionCameraConfig
import kotlin.math.min


object GreeCameraConfigFactory  {
    /**
     * 根据设备配置创建一个相匹配的CameraConfig；主要根据设备屏幕的分辨率找到比屏幕分辨率小一级的配置；
     * 在适配、性能与体验之间找到平衡点，最终创建一个比较适合当前设备的 CameraConfig。
     *
     * @param context    [Context]
     * @param lensFacing [CameraSelector.LENS_FACING_BACK] or [CameraSelector.LENS_FACING_FRONT]
     * @return
     */
    fun createDefaultCameraConfig(context: Context, lensFacing: Int): CameraConfig {
        val displayMetrics = context.resources.displayMetrics
        val size =
            min(displayMetrics.widthPixels.toDouble(), displayMetrics.heightPixels.toDouble())
                .toInt()
        // 根据分辨率初始化缺省配置CameraConfig；在此前提下尽可能的找到比屏幕分辨率小一级的配置；在适配、性能与体验之间得有所取舍，找到平衡点。
        if (size > ResolutionCameraConfig.IMAGE_QUALITY_720P) {
            var imageQuality = ResolutionCameraConfig.IMAGE_QUALITY_720P
            if (size > ResolutionCameraConfig.IMAGE_QUALITY_1080P) {
                imageQuality = ResolutionCameraConfig.IMAGE_QUALITY_1080P
            }
            return object : GreeCameraConfig(context, imageQuality) {

                override fun options(builder: CameraSelector.Builder): CameraSelector {
                    if (lensFacing >= 0) {
                        builder.requireLensFacing(lensFacing)
                    }
                    return super.options(builder)
                }

            }
        } else {
            return object : AspectRatioCameraConfig(context) {
                override fun options(builder: CameraSelector.Builder): CameraSelector {
                    if (lensFacing >= 0) {
                        builder.requireLensFacing(lensFacing)
                    }
                    return super.options(builder)
                }
            }
        }
    }
}
