package com.example.debuggingtoolkits.page

import android.graphics.Outline
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelLazy
import com.example.debuggingtoolkits.databinding.AccountActivityFacedDetectionAuthV3Binding
import com.example.scantools.utils.setScreenBrightnessMax

/**
* @author JoeYe
* @date 2023/9/6 16:20
 *
 *
*/
class FaceDetectionAuthV3Activity: AppCompatActivity(){

    private val detailViewModel by ViewModelLazy(FaceDetectionAuthV3ViewModel::class,
        { viewModelStore },
        { defaultViewModelProviderFactory })

    private lateinit var binding: AccountActivityFacedDetectionAuthV3Binding

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var lightThreshold: Int = 10
    private var openFaceLightCheck = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AccountActivityFacedDetectionAuthV3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewModel = detailViewModel
        binding.cameraPreview.clipToOutline = true
        binding.cameraPreview.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                Log.e("test0809", "preview width:${view.width} height:${view.height}")
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2.0f)
            }
        }

        // 开启最大亮度
        setScreenBrightnessMax()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        detailViewModel.init()
    }

    override fun onDestroy() {
        super.onDestroy()

        detailViewModel.onDestroy()
    }
}