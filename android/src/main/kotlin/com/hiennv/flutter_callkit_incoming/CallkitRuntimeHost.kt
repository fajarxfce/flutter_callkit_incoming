package com.hiennv.flutter_callkit_incoming

/** Optional application integration for a media runtime that outlives its activity. */
interface CallkitRuntimeHost {
    fun onCallServiceStarted(callId: String)
    fun onCallServiceStopped(callId: String?)
    fun onCallTaskRemoved()
    fun shouldDeferCallEnd(callId: String): Boolean
}
