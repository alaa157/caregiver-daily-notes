package com.caregiver.mobile.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Cold flow of "this device currently has an internet-capable network"
 * (Task 8d): the top bar shows "offline" while notes wait in the outbox.
 *
 * Uses the default-network callback on API 24+ and an internet-capability
 * callback below that (minSdk 21). Requires ACCESS_NETWORK_STATE — an
 * install-time permission, no runtime prompt. This is a UI hint only: the
 * drain itself is WorkManager's `NetworkType.CONNECTED` constraint, which
 * has its own connectivity detection.
 */
fun networkAvailabilityFlow(context: Context): Flow<Boolean> = callbackFlow {
    @Suppress("DEPRECATION")
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (manager == null) {
        // No connectivity service: don't block the status UI on a guess.
        trySend(true)
        awaitClose { }
        return@callbackFlow
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(manager.isOnline())
        }

        override fun onLost(network: Network) {
            trySend(manager.isOnline())
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(manager.isOnline())
        }
    }
    // Seed with the current state so the first frame is never a guess.
    trySend(manager.isOnline())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        manager.registerDefaultNetworkCallback(callback)
    } else {
        manager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
    }
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()

private fun ConnectivityManager.isOnline(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        activeNetwork
            ?.let { getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } else {
        @Suppress("DEPRECATION")
        activeNetworkInfo?.isConnected == true
    }
