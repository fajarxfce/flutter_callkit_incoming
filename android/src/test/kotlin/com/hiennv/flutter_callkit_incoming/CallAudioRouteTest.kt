package com.hiennv.flutter_callkit_incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallAudioRouteTest {
    private val handset = setOf(CallAudioRoute.EARPIECE, CallAudioRoute.SPEAKER)

    @Test
    fun videoWithoutHeadsetUsesSpeaker() {
        assertEquals(
            CallAudioRoute.SPEAKER,
            selectCallAudioRoute(handset, CallAudioRoute.EARPIECE, speaker = true, force = false),
        )
    }

    @Test
    fun voiceWithoutHeadsetUsesEarpiece() {
        assertEquals(
            CallAudioRoute.EARPIECE,
            selectCallAudioRoute(handset, CallAudioRoute.SPEAKER, speaker = false, force = false),
        )
    }

    @Test
    fun voiceOnSpeakerOnlyDeviceKeepsTheAvailableOutput() {
        assertEquals(
            CallAudioRoute.SPEAKER,
            selectCallAudioRoute(setOf(CallAudioRoute.SPEAKER), CallAudioRoute.SPEAKER, speaker = false, force = false),
        )
    }

    @Test
    fun speakerOffWithoutAnAlternativeReportsTheActualSpeakerRoute() {
        assertEquals(
            CallAudioRoute.SPEAKER,
            selectCallAudioRoute(setOf(CallAudioRoute.SPEAKER), CallAudioRoute.SPEAKER, speaker = false, force = true),
        )
    }

    @Test
    fun speakerOnlyDeviceCanInitializeBeforeTheCurrentRouteArrives() {
        assertEquals(
            CallAudioRoute.SPEAKER,
            selectCallAudioRoute(setOf(CallAudioRoute.SPEAKER), null, speaker = false, force = false),
        )
    }

    @Test
    fun automaticVideoPreservesBluetooth() {
        assertEquals(
            CallAudioRoute.BLUETOOTH,
            selectCallAudioRoute(
                handset + CallAudioRoute.BLUETOOTH,
                CallAudioRoute.BLUETOOTH,
                speaker = true,
                force = false,
            ),
        )
    }

    @Test
    fun automaticVideoPreservesWiredHeadset() {
        assertEquals(
            CallAudioRoute.WIRED_HEADSET,
            selectCallAudioRoute(
                handset + CallAudioRoute.WIRED_HEADSET + CallAudioRoute.BLUETOOTH,
                CallAudioRoute.WIRED_HEADSET,
                speaker = true,
                force = false,
            ),
        )
    }

    @Test
    fun speakerButtonOverridesBluetooth() {
        assertEquals(
            CallAudioRoute.SPEAKER,
            selectCallAudioRoute(
                handset + CallAudioRoute.BLUETOOTH,
                CallAudioRoute.BLUETOOTH,
                speaker = true,
                force = true,
            ),
        )
    }

    @Test
    fun turningSpeakerOffReturnsToHeadset() {
        assertEquals(
            CallAudioRoute.BLUETOOTH,
            selectCallAudioRoute(
                handset + CallAudioRoute.BLUETOOTH,
                CallAudioRoute.SPEAKER,
                speaker = false,
                force = true,
            ),
        )
    }

    @Test
    fun unavailableRequestedOutputDoesNotReportSuccess() {
        assertNull(
            selectCallAudioRoute(
                setOf(CallAudioRoute.EARPIECE),
                CallAudioRoute.EARPIECE,
                speaker = true,
                force = true,
            ),
        )
    }
}
