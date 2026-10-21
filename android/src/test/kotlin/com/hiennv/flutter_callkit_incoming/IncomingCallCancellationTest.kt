package com.hiennv.flutter_callkit_incoming

import android.os.Bundle
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], qualifiers = "w360dp-h800dp-port-mdpi")
class IncomingCallCancellationTest {
    private lateinit var controller: ActivityController<CallkitIncomingActivity>
    private lateinit var activity: CallkitIncomingActivity
    private lateinit var notifications: CallkitNotificationManager
    private lateinit var incoming: Bundle

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        // Android grants this merged signature permission at install time.
        // Robolectric's pre-33 permission shadow needs the grant explicitly.
        shadowOf(context).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        incoming = Data(mapOf("id" to "call-current", "duration" to 45000L)).toBundle()
        controller = Robolectric.buildActivity(
            CallkitIncomingActivity::class.java,
            CallkitIncomingActivity.getIntent(context, incoming),
        ).setup()
        activity = controller.get()
        notifications = CallkitNotificationManager(context, null)
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun cancellingTheRingingCallClosesItsIncomingScreen() {
        notifications.clearIncomingNotification(incoming, false)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("The incoming screen must close before the ring timeout", activity.isFinishing)
    }

    @Test
    fun cancellationBroadcastCanReachTheRegisteredActivityReceiver() {
        notifications.clearIncomingNotification(incoming, false)

        val context = RuntimeEnvironment.getApplication()
        val broadcast = shadowOf(context).broadcastIntents.last()
        assertNull(
            "An activity component prevents Android from delivering to a registered receiver",
            broadcast.component,
        )
    }

    @Test
    fun cancellingAnOlderCallDoesNotCloseTheCurrentIncomingScreen() {
        val oldCall = Data(mapOf("id" to "call-older")).toBundle()
        notifications.clearIncomingNotification(oldCall, false)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(activity.isFinishing)
    }

    @Test
    fun acceptanceKeepsTheExistingDelayedScreenTransition() {
        notifications.clearIncomingNotification(incoming, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(activity.isFinishing)
    }
}
