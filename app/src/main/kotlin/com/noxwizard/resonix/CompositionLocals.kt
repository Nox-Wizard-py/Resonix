package com.noxwizard.resonix

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.staticCompositionLocalOf
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.playback.DownloadUtil
import com.noxwizard.resonix.playback.PlayerConnection
import com.noxwizard.resonix.utils.SyncUtils

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> {
    error("CompositionLocal LocalDatabase not present")
}

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> {
    null
}

val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> {
    error("CompositionLocal LocalDownloadUtil not present")
}

val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> {
    error("CompositionLocal LocalSyncUtils not present")
}

val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<WindowInsets> {
    error("CompositionLocal LocalPlayerAwareWindowInsets not present")
}
