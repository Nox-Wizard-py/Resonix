package com.noxwizard.resonix.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.noxwizard.resonix.BuildConfig
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.TokenResponse
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyAuthManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    // Lazy: AuthorizationService creates Chrome Custom Tab bindings — defer until first use
    private val authService by lazy { AuthorizationService(context) }
    private val prefs by lazy { context.getSharedPreferences("spotify_auth_prefs", Context.MODE_PRIVATE) }
    
    // Configuration

    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = Uri.parse(BuildConfig.SPOTIFY_REDIRECT_URI)
    private val authEndpoint = Uri.parse("https://accounts.spotify.com/authorize")
    private val tokenEndpoint = Uri.parse("https://accounts.spotify.com/api/token")
    
    // Scopes needed for reading playlists (public) and potentially private ones if the user created them
    private val scopes = listOf("playlist-read-private", "playlist-read-collaborative")

    private var _authState: AuthState? = null
    private val authState: AuthState
        get() {
            if (_authState == null) {
                val jsonString = prefs.getString("auth_state", null)
                _authState = if (jsonString != null) {
                    try {
                        AuthState.jsonDeserialize(jsonString)
                    } catch (e: JSONException) {
                        AuthState()
                    }
                } else {
                    AuthState()
                }
            }
            return _authState!!
        }

    /**
     * Starts the Authorization Code Flow.
     * Takes an ActivityResultLauncher to launch the intent.
     */
    fun startAuth(launcher: ActivityResultLauncher<Intent>) {
        if (clientId.isBlank()) {
            println("[SpotifyAuthManager] SPOTIFY_CLIENT_ID is not configured. Cannot start auth.")
            return
        }

        val serviceConfig = AuthorizationServiceConfiguration(authEndpoint, tokenEndpoint)
        
        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
            .setScopes(scopes)
            // PKCE is enabled by default in AppAuth for code flow
            .build()

        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        launcher.launch(authIntent)
    }

    /**
     * Handle the result from the Auth Activity.
     */
    fun handleAuthResponse(intent: Intent, onComplete: (Boolean) -> Unit) {
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null) {
            authState.update(response, ex)
            persistAuthState()
            
            // Exchange code for token
        authService.performTokenRequest(
            response.createTokenExchangeRequest()
        ) { tokenResponse: net.openid.appauth.TokenResponse?, ex: AuthorizationException? ->
            authState.update(tokenResponse, ex)
            persistAuthState()
            
            onComplete(tokenResponse != null)
        }
        } else {
            onComplete(false)
        }
    }

    /**
     * Returns a valid Access Token, refreshing it if necessary.
     * This is a suspend function as it might need network to refresh.
     */
    fun getAccessToken(callback: (String?) -> Unit) {
        println("[SpotifyAuthManager] Requesting access token. Authorized: ${isAuthorized()}")
        // performActionWithFreshTokens automatically handles refreshing if expired
        authState.performActionWithFreshTokens(authService) { accessToken: String?, idToken: String?, ex: AuthorizationException? ->
            if (ex != null) {
                println("[SpotifyAuthManager] Token refresh failed: ${ex.message}")
                // Token refresh failed
                callback(null)
            } else {
                println("[SpotifyAuthManager] Token retrieved successfully: ${accessToken?.take(10)}...")
                callback(accessToken)
            }
        }
    }
    
    fun isAuthorized(): Boolean {
        return authState.isAuthorized
    }
    
    fun logout() {
        _authState = AuthState()
        persistAuthState()
    }

    private fun persistAuthState() {
        prefs.edit().putString("auth_state", authState.jsonSerializeString()).apply()
    }
    
    fun dispose() {
        authService.dispose()
    }
}
