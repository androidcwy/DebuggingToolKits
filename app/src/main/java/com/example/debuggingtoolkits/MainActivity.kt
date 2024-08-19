package com.example.debuggingtoolkits

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import com.example.debuggingtoolkits.databinding.ActivityMainBinding
import com.example.debuggingtoolkits.page.FaceDetectionAuthV3Activity
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFaceDetect.setOnClickListener {
            XXPermissions.with(this)
                .permission(Permission.CAMERA)
                .request { _, allGranted ->
                    if (allGranted) {
                        startActivity(Intent(this, FaceDetectionAuthV3Activity::class.java))
                    }
                }
        }
    }
}