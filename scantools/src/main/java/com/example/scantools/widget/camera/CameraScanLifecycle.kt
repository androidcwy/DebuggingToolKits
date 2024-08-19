package com.example.scantools.widget.camera

import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.analyze.Analyzer

/**
 * @author JoeYe
 * @date 2023/7/19 09:15
 */
class CameraScanLifecycle<T>(
    activity: ComponentActivity, previewView: PreviewView
) : GreeCameraScan<T>(activity, previewView.findViewTreeLifecycleOwner(), previewView) {

    init {
//        setPlayBeep(true)
//        setVibrate(true)
    }

    fun callback(resultCallback: OnScanResultCallback<T>) = apply {
        setOnScanResultCallback(resultCallback)
    }

    fun analyzeListener(listener: Analyzer.OnAnalyzeListener<AnalyzeResult<T>>?) = apply {
        mOnAnalyzeListener = listener
    }

    fun analyzer(analyzer: Analyzer<T>) = apply {
        setAnalyzer(analyzer)
    }

    fun life(lifecycleOwner: LifecycleOwner) = apply {
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (Lifecycle.Event.ON_DESTROY == event) release()
            }
        })
    }

}