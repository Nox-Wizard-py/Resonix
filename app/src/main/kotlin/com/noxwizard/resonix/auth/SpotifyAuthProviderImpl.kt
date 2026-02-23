package com.noxwizard.resonix.auth

import com.noxwizard.resonix.playlistimport.SpotifyParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class SpotifyAuthProviderImpl @Inject constructor(
    private val authManager: SpotifyAuthManager
) : SpotifyParser.SpotifyAuthProvider {

    override suspend fun getAccessToken(): String? = suspendCoroutine { continuation ->
        if (authManager.isAuthorized()) {
            authManager.getAccessToken { token ->
                continuation.resume(token)
            }
        } else {
            continuation.resume(null)
        }
    }

    override fun isAuthorized(): Boolean {
        return authManager.isAuthorized()
    }
}
