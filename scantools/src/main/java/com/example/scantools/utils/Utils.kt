package com.example.scantools.utils

/**
 * @author JoeYe
 * @date 2023/7/7 14:02
 */
inline fun <R> notNull(vararg args: Any?, block: () -> R) =
    when (args.filterNotNull().size) {
        args.size -> block()
        else -> null
    }


fun String?.isEmptyOrNull(): Boolean {
    return this.isNullOrEmpty() || this == "null"
}

