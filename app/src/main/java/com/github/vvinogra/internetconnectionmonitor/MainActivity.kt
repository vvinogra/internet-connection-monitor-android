package com.github.vvinogra.internetconnectionmonitor

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.github.vvinogra.internetconnectionmonitor.data.NetworkConnectionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var networkConnectionManager: NetworkConnectionManager

    private lateinit var tvIsNetworkConnected: TextView
    private lateinit var tvNetworkState: TextView

    private lateinit var btnStartListen: Button
    private lateinit var btnStopListen: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        tvIsNetworkConnected = findViewById(R.id.tvIsNetworkConnected)
        tvNetworkState = findViewById(R.id.tvNetworkState)

        btnStartListen = findViewById(R.id.btnStartListen)
        btnStopListen = findViewById(R.id.btnStopListen)

        btnStartListen.setOnClickListener {
            networkConnectionManager.startListenNetworkState()
        }

        btnStopListen.setOnClickListener {
            networkConnectionManager.stopListenNetworkState()
        }

        networkConnectionManager.networkStateFlow
            .onEach {
                tvNetworkState.text = getString(R.string.network_state_placeholder, it)
            }
            .launchIn(lifecycleScope)

        networkConnectionManager.isNetworkConnectedFlow
            .onEach {
                @StringRes val res = if (it) {
                    R.string.network_is_connected
                } else {
                    R.string.network_is_disconnected
                }

                tvIsNetworkConnected.setText(res)
            }
            .launchIn(lifecycleScope)
    }
}