package com.hiennv.flutter_callkit_incoming.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.ImageViewCompat
import com.hiennv.flutter_callkit_incoming.AppUtils
import com.hiennv.flutter_callkit_incoming.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** A deliberate drag from the centre; the two end targets are not tap buttons. */
class CallSwipeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    var onAccept: (() -> Unit)? = null
    var onDecline: (() -> Unit)? = null

    private val track: View
    private val thumb: ImageView
    private val acceptTarget: View
    private val declineTarget: View
    private val acceptIcon: ImageView
    private val declineIcon: ImageView
    private val hint: TextView
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var acceptLabel = context.getString(R.string.text_accept)
    private var declineLabel = context.getString(R.string.text_decline)
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var initialTranslation = 0f
    private var resolved = false

    init {
        inflate(context, R.layout.view_call_swipe, this)
        track = findViewById(R.id.callSwipeTrack)
        thumb = findViewById(R.id.callSwipeThumb)
        acceptTarget = findViewById(R.id.callSwipeAcceptTarget)
        declineTarget = findViewById(R.id.callSwipeDeclineTarget)
        acceptIcon = findViewById(R.id.callSwipeAcceptIcon)
        declineIcon = findViewById(R.id.callSwipeDeclineIcon)
        hint = findViewById(R.id.callSwipeHint)
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.text_swipe_to_answer)
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (resolved) return
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.callSwipeAnswerAction, acceptLabel,
                ))
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.callSwipeDeclineAction, declineLabel,
                ))
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                return when (action) {
                    R.id.callSwipeAnswerAction -> resolve(accept = true)
                    R.id.callSwipeDeclineAction -> resolve(accept = false)
                    else -> super.performAccessibilityAction(host, action, args)
                }
            }
        })
    }

    fun setActionLabels(accept: String, decline: String) {
        acceptLabel = accept
        declineLabel = decline
    }

    fun setCallType(isVideo: Boolean) {
        val icon = if (isVideo) R.drawable.ic_video else R.drawable.ic_call_accept
        acceptIcon.setImageResource(icon)
        thumb.setImageResource(icon)
    }

    fun setAcceptColor(color: Int) {
        acceptIcon.background = AppUtils.createCircleDrawable(color)
    }

    fun setDeclineColor(color: Int) {
        declineIcon.background = AppUtils.createCircleDrawable(color)
    }

    fun setThumbColor(color: Int) {
        ImageViewCompat.setImageTintList(thumb, ColorStateList.valueOf(color))
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = true

    @SuppressLint("ClickableViewAccessibility") // TalkBack has explicit Answer/Decline actions.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (resolved || !isEnabled) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                thumb.animate().cancel()
                val bounds = boundsOf(thumb).apply {
                    offset(thumb.translationX.roundToInt(), 0)
                    inset(-touchSlop, -touchSlop)
                }
                if (!bounds.contains(event.x.toInt(), event.y.toInt())) return true
                pointerId = event.getPointerId(0)
                downX = event.x
                initialTranslation = thumb.translationX
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                val trackBounds = boundsOf(track)
                if (event.getY(index) < trackBounds.top - touchSlop ||
                    event.getY(index) > trackBounds.bottom + touchSlop) {
                    resetGesture()
                    return true
                }
                val centre = boundsOf(thumb).exactCenterX()
                val left = boundsOf(declineTarget).exactCenterX() - centre
                val right = boundsOf(acceptTarget).exactCenterX() - centre
                val displacement = (initialTranslation + event.getX(index) - downX)
                    .coerceIn(left, right)
                thumb.translationX = displacement
                val progress = if (displacement >= 0f) displacement / right else displacement / left
                val ready = progress >= 0.85f && abs(displacement) > touchSlop
                hint.setText(when {
                    ready && displacement > 0f -> R.string.text_release_to_answer
                    ready -> R.string.text_release_to_decline
                    else -> R.string.text_swipe_to_answer
                })
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (ready) resolve(accept = displacement > 0f) else resetGesture()
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> resetGesture()
        }
        return true
    }

    private fun boundsOf(view: View): Rect = Rect().also {
        view.getDrawingRect(it)
        offsetDescendantRectToMyCoords(view, it)
    }

    private fun resolve(accept: Boolean): Boolean {
        if (resolved || !isEnabled) return false
        resolved = true
        pointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
        thumb.animate().cancel()
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (accept) onAccept?.invoke() else onDecline?.invoke()
        return true
    }

    private fun resetGesture() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
        hint.setText(R.string.text_swipe_to_answer)
        thumb.animate().translationX(0f).setDuration(180L).start()
    }

    override fun onDetachedFromWindow() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        thumb.animate().cancel()
        super.onDetachedFromWindow()
    }
}
