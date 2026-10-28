package com.hiennv.flutter_callkit_incoming

import android.Manifest
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.telecom.ConnectionRequest
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class CallkitConnectionServiceTest {
    private val controller = Robolectric.buildService(CallkitConnectionService::class.java).create()

    @After
    fun tearDown() {
        CallkitConnection.clearAll()
        controller.destroy()
    }

    @Test
    fun telecomReceivesTheActualCallTypeInBothDirections() {
        for (type in listOf(0, 1)) {
            val extras = Bundle().apply {
                putBundle(
                    CallkitConnection.EXTRA_CALL_BUNDLE,
                    Data(mapOf("id" to "call-$type", "type" to type)).toBundle(),
                )
            }
            val request = ConnectionRequest(null, Uri.parse("tel:test"), extras)
            val expected = if (type == 1) VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY
            val incoming = controller.get().onCreateIncomingConnection(null, request)
            assertEquals(expected, incoming.videoState)
            incoming.destroy()
            val outgoing = controller.get().onCreateOutgoingConnection(null, request)
            assertEquals(expected, outgoing.videoState)
            outgoing.destroy()
        }
    }

    @Test
    fun telecomReceivesVideoTypeBeforeCreatingTheConnection() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.MANAGE_OWN_CALLS)
        val telecom = shadowOf(context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager)
        telecom.setCallPhonePermission(true)
        val receiver = CallkitIncomingBroadcastReceiver()
        for (type in listOf(0, 1)) {
            val data = Data(mapOf("id" to "register-$type", "type" to type)).toBundle()
            val expected = if (type == 1) VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY
            receiver.onReceive(context, CallkitIncomingBroadcastReceiver.getIntentIncoming(context, data))
            assertEquals(expected, telecom.lastIncomingCall.extras.getInt(TelecomManager.EXTRA_INCOMING_VIDEO_STATE, -1))
            receiver.onReceive(context, CallkitIncomingBroadcastReceiver.getIntentStart(context, data))
            assertEquals(expected, telecom.lastOutgoingCall.extras.getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, -1))
            assertEquals(type == 1, telecom.lastOutgoingCall.extras.getBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE))
        }
    }

    @Test
    fun outgoingVideoDoesNotForceSpeakerOverAConnectedHeadset() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.MANAGE_OWN_CALLS)
        val telecom = shadowOf(context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager)
        telecom.setCallPhonePermission(true)
        val audio = shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        for (deviceType in listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)) {
            audio.setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder().setType(deviceType).build()))
            val data = Data(mapOf("id" to "headset-$deviceType", "type" to 1)).toBundle()
            CallkitIncomingBroadcastReceiver().onReceive(context, CallkitIncomingBroadcastReceiver.getIntentStart(context, data))
            assertFalse(telecom.lastOutgoingCall.extras.getBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE))
        }
    }
}
