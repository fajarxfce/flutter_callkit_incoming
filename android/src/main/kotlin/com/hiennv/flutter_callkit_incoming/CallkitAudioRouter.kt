package com.hiennv.flutter_callkit_incoming

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.Connection
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executor

/** Routes a registered call through Telecom, which owns its communication device. */
@RequiresApi(Build.VERSION_CODES.M)
internal class CallkitAudioRouter(
    private val callId: String,
    private val connection: Connection,
) {
    companion object {
        private val handler = Handler(Looper.getMainLooper())
        private val executor = Executor { handler.post(it) }
        private val routers = mutableMapOf<String, CallkitAudioRouter>()
        private val requests = mutableMapOf<String, RouteRequest>()
        private const val REQUEST_TIMEOUT_MS = 5000L

        // startCall returns before Telecom creates its Connection. Keep the
        // request until that connection receives its initial endpoints/state.
        fun request(callId: String, speaker: Boolean, force: Boolean, result: MethodChannel.Result) {
            requests[callId]?.fail("A newer audio route was requested")
            val request = RouteRequest(callId, speaker, force, result)
            requests[callId] = request
            handler.postDelayed(request.timeout, REQUEST_TIMEOUT_MS)
            routers[callId]?.applyRequest()
        }

        private class RouteRequest(
            val callId: String,
            val speaker: Boolean,
            val force: Boolean,
            val result: MethodChannel.Result,
        ) {
            var target: CallAudioRoute? = null
            val timeout = Runnable { fail("Telecom did not confirm the audio route") }

            fun complete(route: CallAudioRoute) {
                if (requests[callId] !== this) return
                requests.remove(callId)
                handler.removeCallbacks(timeout)
                result.success(route == CallAudioRoute.SPEAKER)
            }

            fun fail(message: String) {
                if (requests[callId] !== this) return
                requests.remove(callId)
                handler.removeCallbacks(timeout)
                result.error("CALL_AUDIO_ROUTE_FAILED", message, null)
            }
        }
    }

    private var currentRoute: CallAudioRoute? = null
    private var legacyState: CallAudioState? = null
    private var endpoints = emptyList<CallEndpoint>()

    init {
        routers[callId] = this
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onAvailableEndpointsChanged(available: List<CallEndpoint>) {
        endpoints = available
        applyRequest()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun onEndpointChanged(endpoint: CallEndpoint) {
        val route = endpoint.endpointType.toCallAudioRoute() ?: return
        reportRoute(route)
        applyRequest()
    }

    @Suppress("DEPRECATION")
    fun onAudioStateChanged(state: CallAudioState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        legacyState = state
        state.route.toLegacyCallAudioRoute()?.let(::reportRoute)
        applyRequest()
    }

    fun dispose() {
        if (routers[callId] !== this) return
        routers.remove(callId)
        requests[callId]?.fail("The call ended before its audio route changed")
    }

    private fun reportRoute(route: CallAudioRoute) {
        currentRoute = route
        FlutterCallkitIncomingPlugin.sendEvent(
            CallkitConstants.ACTION_CALL_AUDIO_STATE_CHANGE,
            mapOf("id" to callId, "isSpeakerEnabled" to (route == CallAudioRoute.SPEAKER)),
        )
        val request = requests[callId]
        if (request?.target == route) request.complete(route)
    }

    @Suppress("DEPRECATION")
    private fun applyRequest() {
        val request = requests[callId] ?: return
        if (request.target != null) return
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val available = if (modern) {
            endpoints.mapNotNull { it.endpointType.toCallAudioRoute() }.toSet()
        } else {
            val state = legacyState ?: return
            CallAudioRoute.entries.filter { state.supportedRouteMask and it.legacyRoute != 0 }.toSet()
        }
        if (available.isEmpty()) return
        val target = selectCallAudioRoute(available, currentRoute, request.speaker, request.force)
        if (target == null) {
            request.fail("The requested audio output is unavailable")
            return
        }
        request.target = target
        if (currentRoute == target) {
            request.complete(target)
            return
        }
        try {
            if (modern) {
                val endpoint = endpoints.first { it.endpointType.toCallAudioRoute() == target }
                connection.requestCallEndpointChange(
                    endpoint,
                    executor,
                    object : OutcomeReceiver<Void, CallEndpointException> {
                        override fun onResult(result: Void?) {
                            // Telecom confirms the switch here; the endpoint callback
                            // also reports changes initiated by the OS/headset.
                            reportRoute(target)
                        }

                        override fun onError(error: CallEndpointException) {
                            request.fail(error.message ?: "Telecom rejected the audio route")
                        }
                    },
                )
            } else {
                connection.setAudioRoute(target.legacyRoute)
            }
        } catch (error: Exception) {
            request.fail(error.message ?: "The audio route could not be changed")
        }
    }
}

@Suppress("DEPRECATION")
internal val CallAudioRoute.legacyRoute: Int
    get() = when (this) {
        CallAudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
        CallAudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        CallAudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
        CallAudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
    }

@Suppress("DEPRECATION")
private fun Int.toLegacyCallAudioRoute(): CallAudioRoute? =
    CallAudioRoute.entries.firstOrNull { it.legacyRoute == this }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun Int.toCallAudioRoute(): CallAudioRoute? = when (this) {
    CallEndpoint.TYPE_SPEAKER -> CallAudioRoute.SPEAKER
    CallEndpoint.TYPE_EARPIECE -> CallAudioRoute.EARPIECE
    CallEndpoint.TYPE_BLUETOOTH -> CallAudioRoute.BLUETOOTH
    CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioRoute.WIRED_HEADSET
    else -> null
}
