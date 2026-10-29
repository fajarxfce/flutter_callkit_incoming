package com.hiennv.flutter_callkit_incoming

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class LockedNotificationAnswerTest {
    private lateinit var context: Context
    private lateinit var incoming: Bundle
    private lateinit var notifications: CallkitNotificationManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        incoming = Data(mapOf("id" to "locked-call", "nameCaller" to "Test Caller", "duration" to 45000L)).toBundle()
        notifications = CallkitNotificationManager(context, null)
    }

    @Test
    fun lockedCardAnswersThroughContentWithoutAnUnlockingActionButton() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setKeyguardLocked(true)
        val notification = notifications.getIncomingNotification(incoming)!!.notification
        assertEquals("Ketuk untuk menjawab", notification.extras.getCharSequence("android.text"))
        assertTrue(notification.actions.all { !shadowOf(it.actionIntent).isActivityIntent })
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(CallkitConstants.ACTION_CALL_ACCEPT, intent.action)
        assertEquals(TransparentActivity::class.java.name, intent.component!!.className)
        assertEquals("locked-call", intent.getBundleExtra("data")!!
            .getString(CallkitConstants.EXTRA_CALLKIT_ID))
    }

    @Test
    fun unlockedAnswerPreservesTheDirectActivityLaunch() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setKeyguardLocked(false)
        val notification = notifications.getIncomingNotification(incoming)!!.notification

        assertTrue(shadowOf(notification.actions.last().actionIntent).isActivityIntent)
    }
}
