package com.github.vvinogra.internetconnectionmonitor.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.github.vvinogra.internetconnectionmonitor.data.model.NetworkState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkConnectionManager {
    /**
     * Emits the current network state on every network update
     */
    val networkStateFlow: StateFlow<NetworkState>

    /**
     * Emits [Boolean] value only when the current network becomes available or unavailable.
     */
    val isNetworkConnectedFlow: StateFlow<Boolean>

    val isNetworkConnected: Boolean

    fun startListenNetworkState()

    fun stopListenNetworkState()
}

@Singleton
class NetworkConnectionManagerImpl @Inject constructor(
    @ApplicationContext context: Context,
    coroutineScope: CoroutineScope
) : NetworkConnectionManager {

    private val connectivityManager: ConnectivityManager = context.getSystemService()!!

    private val networkCallback = NetworkCallback()

    /**
     * On Android 9, [ConnectivityManager.NetworkCallback.onBlockedStatusChanged] is not called after
     * the [ConnectivityManager.NetworkCallback.onAvailable] callback hence we should not use the default value
     */
    private val _isBlockedFlow = MutableStateFlow<Boolean?>(null)
    private val _isAvailableFlow = MutableStateFlow(false)
    private val _networkCapabilitiesFlow = MutableStateFlow<NetworkCapabilities?>(null)

    private val _currentNetwork = combine(
        _isBlockedFlow,
        _isAvailableFlow,
        _networkCapabilitiesFlow,
        ::createCurrentNetwork
    ).stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = CurrentNetwork(
            networkCapabilities = _networkCapabilitiesFlow.value,
            isAvailable = _isAvailableFlow.value,
            isBlocked = _isBlockedFlow.value
        )
    )

    override val networkStateFlow: StateFlow<NetworkState> =
        _currentNetwork.map(::mapToNetworkState)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = mapToNetworkState(_currentNetwork.value)
            )

    override val isNetworkConnectedFlow: StateFlow<Boolean> =
        networkStateFlow
            .map { it == NetworkState.CONNECTED }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = networkStateFlow.value == NetworkState.CONNECTED
            )

    override val isNetworkConnected: Boolean
        get() = _currentNetwork.value.isConnected()

    override fun startListenNetworkState() {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    override fun stopListenNetworkState() {
        // Using "runCatching" to handle exception in case we didn't start listening
        // before calling this function
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isAvailableFlow.value = true
        }

        override fun onLost(network: Network) {
            _isAvailableFlow.value = false
            _networkCapabilitiesFlow.value = null
        }

        override fun onUnavailable() {
            _isAvailableFlow.value = false
            _networkCapabilitiesFlow.value = null
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _networkCapabilitiesFlow.value = networkCapabilities
        }

        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            _isBlockedFlow.value = blocked
        }
    }

    private fun createCurrentNetwork(
        isBlocked: Boolean?,
        isAvailable: Boolean,
        networkCapabilities: NetworkCapabilities?
    ) = CurrentNetwork(
        networkCapabilities = networkCapabilities,
        isAvailable = isAvailable,
        isBlocked = isBlocked
    ).also {
        Timber.d("Network state changed; current network = $it")
    }

    private fun mapToNetworkState(currentNetwork: CurrentNetwork) =
        if (currentNetwork.isConnected().also { isConnected ->
                Timber.d("mapToNetworkState; isConnected = $isConnected")
            }) {
            NetworkState.CONNECTED
        } else {
            NetworkState.NOT_CONNECTED
        }
}

private data class CurrentNetwork(
    val networkCapabilities: NetworkCapabilities?,
    val isAvailable: Boolean,
    val isBlocked: Boolean?
)

private fun CurrentNetwork.isConnected(): Boolean {
    return isAvailable && isBlocked != true && networkCapabilities.isNetworkCapabilitiesValid()
}

private fun NetworkCapabilities?.isNetworkCapabilitiesValid(): Boolean = when {
    this == null -> false
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            (hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) -> true
    else -> false
}