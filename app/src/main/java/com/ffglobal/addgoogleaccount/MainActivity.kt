package com.ffglobal.addgoogleaccount

import android.accounts.AccountManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var isAccountStarted = false
    private var restrictionsReceiver: BroadcastReceiver? = null
    private var elapsedMillis = 0L
    private val maxWaitMillis = 10000L // Up to 10 seconds for server/UEM configuration sync
    private val pollIntervalMillis = 500L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Listen for application restrictions updates from the MDM server
        restrictionsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                checkAndProceed()
            }
        }

        val filter = IntentFilter("android.intent.action.APPLICATION_RESTRICTIONS_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(restrictionsReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(restrictionsReceiver, filter)
        }

        // Start polling for the latest configuration
        checkAndProceed()
    }

    private fun checkAndProceed() {
        if (isAccountStarted) return
        val restrictionsManager = getSystemService(RESTRICTIONS_SERVICE) as? RestrictionsManager
        val username = restrictionsManager?.applicationRestrictions?.getString("username")

        if (!username.isNullOrBlank()) {
            proceedWithAccountAdd(username)
        } else {
            elapsedMillis += pollIntervalMillis
            if (elapsedMillis >= maxWaitMillis) {
                // Timeout reached after 10 seconds, proceed
                proceedWithAccountAdd(null)
            } else {
                handler.postDelayed(
                    {
                        checkAndProceed()
                    },
                    pollIntervalMillis,
                )
            }
        }
    }

    private fun proceedWithAccountAdd(username: String?) {
        if (isAccountStarted) return
        isAccountStarted = true

        // Clean up receiver and handler callbacks
        restrictionsReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            restrictionsReceiver = null
        }
        handler.removeCallbacksAndMessages(null)

        val options = Bundle().apply {
            username?.let { putString(AccountManager.KEY_ACCOUNT_NAME, it) } // unverified hint
        }

        AccountManager.get(this).addAccount(
            "com.google", null, null, options, this,
            { future ->
                try {
                    future.result
                } catch (_: Exception) {
                    // report failure to the user or UEM; don't include the username
                } finally {
                    finish()
                }
            },
            null,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        restrictionsReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            restrictionsReceiver = null
        }
        handler.removeCallbacksAndMessages(null)
    }
}
