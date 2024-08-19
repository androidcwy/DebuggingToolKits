package com.example.scantools.utils

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import java.lang.reflect.InvocationTargetException

/**
 * @author JoeYe
 * @date 2023/2/4 14:30
 */
object AppGlobal {
    //获取版本号(内部识别号)
    fun getVersionCode(context: Context): Int {
        return try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            pi.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            0
        }
    }

    fun isActivityTop(cls: Class<*>, context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = manager.getRunningTasks(1)[0].topActivity!!.className
        return name == cls.name
    }

    private var sApplication: Application? = null
    val application: Application?
        get() {
            if (sApplication == null) {
                try {
                    sApplication = Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null) as Application
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                } catch (e: NoSuchMethodException) {
                    e.printStackTrace()
                } catch (e: ClassNotFoundException) {
                    e.printStackTrace()
                }
            }
            return sApplication
        }
}