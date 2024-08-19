package com.example.scantools.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import com.example.scantools.utils.dp

/**
 * @author JoeYe
 * @date 2024/6/18 09:33
 */
class RoundPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val overlayFaultHeight = 50.dp

    val previewView: PreviewView = PreviewView(context).apply {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = view.width.coerceAtMost(view.height) / 2f
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
    }

    val titleOverlay = FrameLayout(context).apply {
        setBackgroundColor(Color.parseColor("#61000000"))
    }

    val titleText = TextView(context).apply {
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
    }

    init {
        addView(previewView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(titleOverlay, LayoutParams(LayoutParams.MATCH_PARENT, overlayFaultHeight))
        titleOverlay.addView(
            titleText,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
    }

    fun setTitle(title: String?) {
        titleText.text = title
        val visibility = if (title.isNullOrEmpty()) View.GONE else View.VISIBLE
        titleOverlay.visibility = visibility
        titleText.visibility = visibility
    }
}