package com.greg7gkb.readout

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.greg7gkb.readout.onboarding.OnboardingPermissions
import com.greg7gkb.readout.onboarding.OnboardingScreen
import com.greg7gkb.readout.session.SessionOrchestrator
import com.greg7gkb.readout.session.SessionState
import com.greg7gkb.readout.settings.SettingsScreen
import com.greg7gkb.readout.ui.theme.ReadoutTheme
import com.greg7gkb.readout.wake.ManualActivator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var orchestrator: SessionOrchestrator

    @Inject
    lateinit var manualActivator: ManualActivator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "starting orchestrator in activity scope (foreground service takes over in Step 11)")
        orchestrator.start(lifecycleScope)

        setContent {
            ReadoutTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    val context = LocalContext.current
                    var onboarded by remember {
                        mutableStateOf(OnboardingPermissions.allGranted(context))
                    }
                    var showSettings by remember { mutableStateOf(false) }

                    when {
                        !onboarded -> OnboardingScreen(
                            padding = padding,
                            onComplete = { onboarded = true },
                        )

                        showSettings -> {
                            BackHandler { showSettings = false }
                            SettingsScreen(onBack = { showSettings = false })
                        }

                        else -> ReadoutHome(
                            padding = padding,
                            state = orchestrator.state,
                            onTriggerClick = { manualActivator.trigger() },
                            onSettingsClick = { showSettings = true },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "Readout"
    }
}

@Composable
private fun ReadoutHome(
    padding: PaddingValues,
    state: StateFlow<SessionState>,
    onTriggerClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val current by state.collectAsState()
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        TextButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Text("Settings")
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Readout", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = current.describe(), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onTriggerClick) {
                Text("Trigger activation")
            }
        }
    }
}

private fun SessionState.describe(): String = when (this) {
    SessionState.Idle -> "Idle"
    is SessionState.Listening -> "Listening…"
    is SessionState.Thinking -> "Thinking about: $question"
    is SessionState.Speaking -> "Speaking: $answer"
    is SessionState.Error -> "Error: $message"
}
