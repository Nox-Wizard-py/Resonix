package com.noxwizard.resonix

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.playback.DownloadUtil
import com.noxwizard.resonix.playback.MusicService
import com.noxwizard.resonix.playback.PlayerConnection
import com.noxwizard.resonix.ui.ResonixApp
import com.noxwizard.resonix.utils.SyncUtils
import com.noxwizard.resonix.utils.Updater
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import com.resonix.sync.ResonixSync

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isBound = false
    private var currentIntent by mutableStateOf<Intent?>(null)

    // Manage latestVersionName state here or inside ResonixApp?
    // It's fetched in setContent in original code but stored in a variable.
    // We can pass it as a static initial value, and let ResonixApp update it locally.
    // Or we can keep it as state here.
    // Original code: var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)
    // And LaunchedEffect updated it.
    // I moved that logic to ResonixApp. So here just pass BuildConfig.VERSION_NAME.
    
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val binder = service as MusicService.MusicBinder
                playerConnection = PlayerConnection(
                    context = this@MainActivity,
                    binder = binder,
                    database = database,
                    scope = lifecycleScope
                )
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerConnection = null
                isBound = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ResonixSync.init(
            context = applicationContext,
            serverUrl = "wss://resonix-0pvb.onrender.com/ws"
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Handle initial intent
        currentIntent = intent

        setContent {
            ResonixApp(
                database = database,
                playerConnection = playerConnection,
                downloadUtil = downloadUtil,
                syncUtils = syncUtils,
                latestVersionName = BuildConfig.VERSION_NAME,
                currentIntent = currentIntent,
                onSetSystemBarAppearance = { isDark ->
                    setSystemBarAppearance(isDark)
                },
                onSetWindowFlags = { disable ->
                    if (disable) {
                        try {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } catch (_: Exception) {
                        }
                    } else {
                        try {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } catch (_: Exception) {
                        }
                    }
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        try {
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ResonixSync.destroy()
        playerConnection = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent property
        currentIntent = intent // Trigger recomposition in ResonixApp
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    companion object {
        const val ACTION_LIBRARY = "com.noxwizard.resonix.LIBRARY"
        const val ACTION_SEARCH = "com.noxwizard.resonix.SEARCH"
        const val ACTION_RECOGNITION = "com.noxwizard.resonix.RECOGNITION"
        const val EXTRA_AUTO_START_RECOGNITION = "extra_auto_start_recognition"
    }
}
