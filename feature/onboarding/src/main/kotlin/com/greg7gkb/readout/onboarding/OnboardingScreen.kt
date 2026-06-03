package com.greg7gkb.readout.onboarding

import android.Manifest
import android.content.Context
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
import com.greg7gkb.readout.screen.AccessibilityServiceStatus

/**
 * Stepped onboarding flow. The accessibility-service grant comes first
 * because without screen reading nothing else Readout does is useful;
 * runtime permissions (microphone, notifications) come second and share
 * a single system-prompt dialog.
 *
 * Re-checks requirement state on lifecycle resume so the user toggling
 * a setting and returning advances the flow without a manual refresh.
 */
@Composable
fun OnboardingScreen(
    padding: PaddingValues,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(OnboardingPermissions.missing(context)) }

    // Captured once so RuntimePermissionsStep knows whether it's the
    // user's first screen (greet) or a follow-up after Screen Reading
    // (acknowledge the prior step).
    val accessibilityInitiallyMissing = remember {
        missing.any { it is OnboardingRequirement.AccessibilityService }
    }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        missing = OnboardingPermissions.missing(context)
        if (missing.isEmpty()) onComplete()
    }

    val needsScreenReading = missing.any { it is OnboardingRequirement.AccessibilityService }
    val runtimeMissing = missing.filterIsInstance<OnboardingRequirement.RuntimePermission>()

    when {
        needsScreenReading -> ScreenReadingStep(padding, context)
        runtimeMissing.isNotEmpty() -> RuntimePermissionsStep(
            padding = padding,
            missing = runtimeMissing,
            isFollowUp = accessibilityInitiallyMissing,
            onGrant = {
                permissionLauncher.launch(runtimeMissing.map { it.name }.toTypedArray())
            },
        )
    }
}

@Composable
private fun ScreenReadingStep(padding: PaddingValues, context: Context) {
    OnboardingStepColumn(padding) {
        Text(
            text = "Welcome to Readout",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ask Readout about whatever's on your screen — it'll listen, look, and speak the answer back.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Screen reading", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "To read what's on your screen when you ask a question. Screen " +
                        "content is read on demand only and sent to the configured language " +
                        "model. It is not logged, stored, or transmitted for any other purpose.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "In the next screen: Downloaded apps → Readout → toggle on.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { context.startActivity(AccessibilityServiceStatus.settingsIntent()) }) {
            Text(text = "Open Accessibility Settings")
        }
    }
}

@Composable
private fun RuntimePermissionsStep(
    padding: PaddingValues,
    missing: List<OnboardingRequirement.RuntimePermission>,
    isFollowUp: Boolean,
    onGrant: () -> Unit,
) {
    OnboardingStepColumn(padding) {
        Text(
            text = if (isFollowUp) "Almost there" else "Welcome to Readout",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isFollowUp) {
                val noun = if (missing.size == 1) "permission" else "permissions"
                "${missing.size} more $noun and Readout is ready."
            } else {
                "Ask Readout about whatever's on your screen — it'll listen, look, and speak the answer back."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        missing.forEach { perm ->
            RuntimePermissionCard(perm)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGrant) {
            Text(text = "Grant permissions")
        }
    }
}

@Composable
private fun RuntimePermissionCard(permission: OnboardingRequirement.RuntimePermission) {
    val (title, reason) = describe(permission)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = reason, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OnboardingStepColumn(
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

private fun describe(permission: OnboardingRequirement.RuntimePermission): Pair<String, String> =
    when (permission.name) {
        Manifest.permission.RECORD_AUDIO ->
            "Microphone" to
                "To hear the wake word and your spoken questions. Audio is processed " +
                "only while a session is active and never recorded to disk."
        Manifest.permission.POST_NOTIFICATIONS ->
            "Notifications" to
                "To show a persistent status while Readout is listening, so the " +
                "microphone is never active without a visible indicator."
        else -> permission.name to "Required for app functionality."
    }
