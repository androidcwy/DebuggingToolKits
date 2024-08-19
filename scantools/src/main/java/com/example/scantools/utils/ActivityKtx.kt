package com.example.scantools.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

/**
 * @author JoeYe
 * @date 2024/6/20 14:15
 */

/**
 * 调节当前屏幕亮度为最亮模式
 */
fun Activity.setScreenBrightnessMax() {
    val window = this.window
    val layoutParams = window.attributes
    layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
    window.attributes = layoutParams
}

fun Context.findActivity(): Activity? {
    if (this is Activity) {
        return this
    }
    val baseContext = (this as ContextWrapper).baseContext ?: return null
    return baseContext.findActivity()
}