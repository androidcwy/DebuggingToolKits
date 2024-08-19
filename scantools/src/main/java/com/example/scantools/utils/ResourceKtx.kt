package com.example.scantools.utils

import androidx.annotation.ArrayRes
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/**
 * @author JoeYe
 * @date 2023/6/19 17:17
 */

fun Any.getGlobalString(@StringRes stringRes: Int, vararg args: Any): String{
    return AppGlobal.application!!.getString(stringRes, *args)
}

fun Any.getGlobalStringArray(@ArrayRes arrayRes: Int): Array<String>{
    return AppGlobal.application!!.resources.getStringArray(arrayRes)
}

fun Any.getGlobalColor(@ColorRes colorRes: Int): Int{
    return ContextCompat.getColor(AppGlobal.application!!, colorRes)
}

fun Any.getGlobalColorArray(@ArrayRes arrayRes: Int): IntArray {
    return AppGlobal.application!!.resources.getIntArray(arrayRes)
}