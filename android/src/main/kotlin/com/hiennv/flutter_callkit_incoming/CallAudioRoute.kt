package com.hiennv.flutter_callkit_incoming

internal enum class CallAudioRoute { SPEAKER, EARPIECE, BLUETOOTH, WIRED_HEADSET }

/** Automatic call setup preserves a headset; an explicit Speaker tap can override it. */
internal fun selectCallAudioRoute(
    available: Set<CallAudioRoute>,
    current: CallAudioRoute?,
    speaker: Boolean,
    force: Boolean,
): CallAudioRoute? {
    if (speaker && force) return CallAudioRoute.SPEAKER.takeIf { it in available }
    val headsets = listOf(CallAudioRoute.BLUETOOTH, CallAudioRoute.WIRED_HEADSET)
    if (current in headsets && current in available) return current
    headsets.firstOrNull { it in available }?.let { return it }
    val target = if (speaker) CallAudioRoute.SPEAKER else CallAudioRoute.EARPIECE
    return target.takeIf { it in available }
}
