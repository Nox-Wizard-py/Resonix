package com.noxwizard.resonix.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noxwizard.resonix.R
import com.noxwizard.resonix.auth.SpotifyAuthManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLoginScreen(
    authManager: SpotifyAuthManager,
    onNavigateBack: () -> Unit
) {
    var isAuthorized by remember { mutableStateOf(authManager.isAuthorized()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            authManager.handleAuthResponse(result.data!!) { success ->
                isAuthorized = success
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link Spotify") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Spotify Logo (Placeholder or Resource)
            // Assuming we might not have a drawable yet, we'll use a generic icon or text
            // If you have R.drawable.ic_spotify, use it. For now, text/icon placeholder.
            Text(
                text = "Spotify",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFF1DB954) // Spotify Green
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (isAuthorized) 
                    "✅ Your account is connected!" 
                else 
                    "Connect your Spotify account to import full playlists without the 100-track limit.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (!isAuthorized) {
                Button(
                    onClick = {
                        authManager.startAuth(launcher)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1DB954), // Spotify Green
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Login with Spotify")
                }
            } else {
                Button(
                    onClick = {
                        authManager.logout()
                        isAuthorized = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}
