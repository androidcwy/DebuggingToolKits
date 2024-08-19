package com.example.scantools.bindings

import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import androidx.databinding.BindingAdapter
import androidx.databinding.ObservableField

/**
 * @Author GZDong
 * @Date 2023/6/8
 */

@BindingAdapter(value = ["clickListener", "quickClick", "clickTimeInterval"], requireAll = false)
fun registerClick(
    anyView: View,
    clickAction: ClickListener?,
    quickClick: Boolean = false,
    clickTimeInterval: Long = 1500L
) {
    if (quickClick) {
        anyView.setOnClickListener {
            clickAction?.callBack?.invoke(anyView)
        }
    } else {
        anyView.setOnClickListener(ClickListenerProxy(clickTimeInterval) {
            clickAction?.callBack?.invoke(anyView)
        })
    }
}

@BindingAdapter(value = ["clickSelf"], requireAll = false)
fun bindClickSelf(
    anyView: View,
    trigger: ObservableField<Int>
) {
    anyView.callOnClick()
}

@BindingAdapter(value = ["onClickListener"], requireAll = false)
fun registerNormalClick(
    anyView: View,
    clickAction: OnClickListener?
) {
    anyView.setOnClickListener(clickAction)
}

@BindingAdapter(value = ["longClickListener"])
fun bindLongClick(
    anyView: View,
    longClickAction: LongClickListener?
) {
    anyView.setOnLongClickListener {
        longClickAction?.callback?.invoke(anyView)
        true
    }
}

class ClickListener(val callBack: ((view: View?) -> Unit)?)

class LongClickListener(val callback: ((view: View?) -> Unit)?)


//点击事件代理，增加防抖功能
private class ClickListenerProxy(
    private val intervalTime: Long,
    private val originListener: OnClickListener?
) : OnClickListener {

    private var lastClick: Long = 0

    override fun onClick(view: View?) {
        //用户手动修改系统时间
        val userChangedSystemTime = System.currentTimeMillis() - lastClick < 0
        if (System.currentTimeMillis() - lastClick >= intervalTime || userChangedSystemTime) {
            originListener?.onClick(view)
            lastClick = System.currentTimeMillis()
        }
    }
}