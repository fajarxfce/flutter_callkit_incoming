package com.hiennv.flutter_callkit_incoming

import android.net.Uri
import android.os.Bundle
import android.telecom.ConnectionRequest
import android.telecom.VideoProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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
}
