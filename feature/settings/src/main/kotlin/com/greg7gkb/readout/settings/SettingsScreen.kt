package com.greg7gkb.readout.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Phase 1 stub. Renders the categories of controls called out in the plan
 * (TTS, wake-word sensitivity, privacy, about). Values are in-memory only
 * and do not yet feed back into TtsEngine or other components — persistence
 * via DataStore lands when these knobs actually drive behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            VoiceSection()
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            WakeWordSection()
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            PrivacySection()
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            AboutSection()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VoiceSection() {
    SectionHeader("Voice")
    var rate by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    LabeledSlider(label = "Speech rate", value = rate, onChange = { rate = it })
    LabeledSlider(label = "Pitch", value = pitch, onChange = { pitch = it })
}

@Composable
private fun WakeWordSection() {
    SectionHeader("Wake word")
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    LabeledSlider(
        label = "Sensitivity",
        value = sensitivity,
        onChange = { sensitivity = it },
        valueRange = 0f..1f,
    )
    Text(
        text = "Wake-word detection lands in Phase 4. Sensitivity has no effect yet.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PrivacySection() {
    SectionHeader("Privacy")
    var cloudAllowed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Allow cloud LLM calls", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "When off, Readout uses only on-device models.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = cloudAllowed, onCheckedChange = { cloudAllowed = it })
    }
}

@Composable
private fun AboutSection() {
    SectionHeader("About")
    Text(text = "Readout v0.1.0 (dev)", style = MaterialTheme.typography.bodyLarge)
    Text(
        text = "Accessibility-first voice Q&A about whatever's on your screen.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0.5f..2.0f,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "$label: ${value.fmt()}")
        Slider(value = value, onValueChange = onChange, valueRange = valueRange)
    }
}

private fun Float.fmt(): String = String.format(Locale.US, "%.1f", this)
