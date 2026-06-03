package com.greg7gkb.readout.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Welcome + permissions screen. Renders cards explaining what each missing
 * permission unlocks, then a single system-prompt request for all of them.
 *
 * Re-checks permission state on lifecycle resume so a user who grants via
 * Settings → app info gets advanced automatically without restart.
 */
@Composable
fun OnboardingScreen(
    padding: PaddingValues,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(OnboardingPermissions.missing(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missing = OnboardingPermissions.missing(context)
                if (missing.isEmpty()) onComplete()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        missing = OnboardingPermissions.missing(context)
        if (missing.isEmpty()) onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome to Readout",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ask Readout about whatever's on your screen — it'll listen, look, and speak the answer back.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        missing.forEach { permission ->
            PermissionCard(permission)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { launcher.launch(missing.toTypedArray()) }) {
            Text(text = "Grant permissions")
        }
    }
}

@Composable
private fun PermissionCard(permission: String) {
    val (title, reason) = describePermission(permission)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = reason, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun describePermission(permission: String): Pair<String, String> = when (permission) {
    Manifest.permission.RECORD_AUDIO ->
        "Microphone" to
            "To hear the wake word and your spoken questions. Audio is processed " +
            "only while a session is active and never recorded to disk."
    Manifest.permission.POST_NOTIFICATIONS ->
        "Notifications" to
            "To show a persistent status while Readout is listening, so the " +
            "microphone is never active without a visible indicator."
    else -> permission to "Required for app functionality."
}
