package com.greg7gkb.readout

import android.os.Bundle
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
import androidx.compose.material3.OutlinedButton
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
import com.greg7gkb.readout.onboarding.OnboardingPermissions
import com.greg7gkb.readout.onboarding.OnboardingScreen
import com.greg7gkb.readout.service.ReadoutService
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

    /**
     * Injected so the UI can observe live state from the same orchestrator
     * the [ReadoutService] is driving. The activity never calls
     * [SessionOrchestrator.start] — that's the service's job.
     */
    @Inject
    lateinit var orchestrator: SessionOrchestrator

    @Inject
    lateinit var manualActivator: ManualActivator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            running = orchestrator.isRunning,
                            onStartClick = { ReadoutService.start(context) },
                            onStopClick = { ReadoutService.stop(context) },
                            onTriggerClick = { manualActivator.trigger() },
                            onSettingsClick = { showSettings = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadoutHome(
    padding: PaddingValues,
    state: StateFlow<SessionState>,
    running: StateFlow<Boolean>,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onTriggerClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val current by state.collectAsState()
    val isRunning by running.collectAsState()
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
            if (isRunning) {
                Button(onClick = onTriggerClick) {
                    Text("Trigger activation")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onStopClick) {
                    Text("Stop session")
                }
            } else {
                Button(onClick = onStartClick) {
                    Text("Start session")
                }
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
