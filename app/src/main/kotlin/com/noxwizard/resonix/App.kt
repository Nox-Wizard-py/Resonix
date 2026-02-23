package com.noxwizard.resonix

import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import com.noxwizard.resonix.constants.*
import com.noxwizard.resonix.extensions.*
import com.noxwizard.resonix.utils.dataStore
import com.noxwizard.resonix.utils.get
import com.noxwizard.resonix.utils.initPreferencesCache
import com.noxwizard.resonix.utils.reportException
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.innertube.models.YouTubeLocale
import com.noxwizard.resonix.kugou.KuGou
import com.noxwizard.resonix.lastfm.LastFM
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import android.content.ComponentCallbacks2
import android.content.Intent
import coil3.imageLoader
import com.noxwizard.resonix.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess
import timber.log.Timber
import java.net.Proxy
import java.util.*

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {
    // Create a proper application scope that will be cancelled when the app is destroyed
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // ✅ PHASE 1: Critical-only init on main thread (<50ms)
        initPreferencesCache(dataStore)
        Timber.plant(Timber.DebugTree())
        try {
            Timber.plant(com.noxwizard.resonix.utils.GlobalLogTree())
        } catch (_: Exception) {}

        // ✅ PHASE 2: Defer heavy config to background
        applicationScope.launch(Dispatchers.Default) {
            initializeYouTubeConfig()
            initializeLastFM()
            initializeProxy()
        }

        applicationScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" } // Previously visitorData was sometimes saved as "null" due to a bug
                        ?: YouTube.visitorData().onFailure {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@App, "Failed to get visitorData.", LENGTH_SHORT).show()
                            }
                            reportException(it)
                        }.getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }

        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    val stack = sw.toString()

                    val intent = Intent(this@App, DebugActivity::class.java).apply {
                        putExtra(DebugActivity.EXTRA_STACK_TRACE, stack)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                    try { Thread.sleep(100) } catch (_: InterruptedException) {}
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    try {
                        defaultHandler?.uncaughtException(thread, throwable)
                    } catch (_: Exception) {
                        android.os.Process.killProcess(android.os.Process.myPid())
                        exitProcess(2)
                    }
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
        applicationScope.launch {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        /*
                         * Workaround to avoid breaking older installations that have a dataSyncId
                         * that contains "||" in it.
                         * If the dataSyncId ends with "||" and contains only one id, then keep the
                         * id before the "||".
                         * If the dataSyncId contains "||" and is not at the end, then keep the
                         * second id.
                         * This is needed to keep using the same account as before.
                         */
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }
        applicationScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        // we now allow user input now, here be the demons. This serves as a last ditch effort to avoid a crash loop
                        Timber.e("Could not parse cookie. Clearing existing cookie. %s", e.message)
                        forgetAccount(this@App)
                    }
                }
        }
        applicationScope.launch {
            dataStore.data
                .map { it[LastFMSessionKey] }
                .distinctUntilChanged()
                .collect { sessionKey ->
                    LastFM.sessionKey = sessionKey
                }
        }
    }

    // ══════════════════════════════════════════════════════
    // Deferred Initialization Helpers
    // ══════════════════════════════════════════════════════

    private fun initializeYouTubeConfig() {
        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }
        if (dataStore[UseLoginForBrowse] != false) {
            YouTube.useLoginForBrowse = true
        }
    }

    private fun initializeLastFM() {
        LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET
        )
        LastFM.sessionKey = dataStore[LastFMSessionKey]
    }

    private fun initializeProxy() {
        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                applicationScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@App, "Failed to parse proxy url.", LENGTH_SHORT).show()
                }
                reportException(e)
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // Memory Pressure Handling
    // ══════════════════════════════════════════════════════

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Clear Coil image memory cache to free RAM
                imageLoader.memoryCache?.clear()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        imageLoader.memoryCache?.clear()
    }

    // ══════════════════════════════════════════════════════
    // Image Loader Configuration
    // ══════════════════════════════════════════════════════

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val cacheSize = dataStore[MaxImageCacheSizeKey]

        val builder = ImageLoader.Builder(this)
            .crossfade(150)
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }

        // Disk cache: disabled if user set cache size to 0
        if (cacheSize != 0) {
            builder.diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes((cacheSize ?: 512) * 1024 * 1024L)
                    .build()
            )
        } else {
            builder.diskCachePolicy(CachePolicy.DISABLED)
        }

        return builder.build()
    }

    companion object {
        lateinit var instance: App
            private set

        fun forgetAccount(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.edit { settings ->
                    settings.remove(InnerTubeCookieKey)
                    settings.remove(VisitorDataKey)
                    settings.remove(DataSyncIdKey)
                    settings.remove(AccountNameKey)
                    settings.remove(AccountEmailKey)
                    settings.remove(AccountChannelHandleKey)
                }
            }
        }
    }
}



