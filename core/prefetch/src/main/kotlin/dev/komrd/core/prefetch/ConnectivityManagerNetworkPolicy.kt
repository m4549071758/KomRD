package dev.komrd.core.prefetch

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ConnectivityManagerNetworkPolicy(
    context: Context,
    private val config: Flow<PrefetchNetworkConfig>,
) : NetworkPolicy {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val decisions: Flow<NetworkDecision> =
        networkTransportFlow()
            .combine(config) { transport, cfg -> decide(transport, cfg) }
            .distinctUntilChanged()

    /** active networkの変化を[NetworkTransport]のストリームへ変換。 */
    private fun networkTransportFlow(): Flow<NetworkTransport> =
        callbackFlow {
            val cm = connectivityManager
            if (cm == null) {
                trySend(NetworkTransport.Offline)
                awaitClose()
                return@callbackFlow
            }
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(transportOf(cm.getNetworkCapabilities(cm.activeNetwork)))
                    }

                    override fun onLost(network: Network) {
                        // activeNetworkが切り替わった直後はonAvailableで上書きされる。切替中はOffline。
                        trySend(transportOf(cm.getNetworkCapabilities(cm.activeNetwork)))
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        caps: NetworkCapabilities,
                    ) {
                        trySend(transportOf(caps))
                    }
                }
            // 初期値: 現在のactive network(無ければOffline)。登録前に送出して初回決定を担保。
            trySend(transportOf(cm.getNetworkCapabilities(cm.activeNetwork)))
            cm.registerDefaultNetworkCallback(callback)
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }

    /** [NetworkCapabilities] → [NetworkTransport]。INTERNET非保持/nullはOffline扱い。 */
    private fun transportOf(caps: NetworkCapabilities?): NetworkTransport {
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkTransport.Offline
        }
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.Mobile
            else -> NetworkTransport.Other
        }
    }
}
