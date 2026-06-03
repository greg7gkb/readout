package com.greg7gkb.readout

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.greg7gkb.readout.audio.SpeechRecognizer
import com.greg7gkb.readout.audio.TtsEngine
import com.greg7gkb.readout.common.di.IoDispatcher
import com.greg7gkb.readout.common.model.Session
import com.greg7gkb.readout.llm.LlmClient
import com.greg7gkb.readout.screen.ScreenReader
import com.greg7gkb.readout.ui.theme.ReadoutTheme
import com.greg7gkb.readout.wake.Activator
import com.greg7gkb.readout.wake.ManualActivator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var llmClient: LlmClient

    @Inject
    lateinit var screenReader: ScreenReader

    @Inject
    lateinit var speechRecognizer: SpeechRecognizer

    @Inject
    lateinit var ttsEngine: TtsEngine

    @Inject
    lateinit var activator: Activator

    @Inject
    lateinit var manualActivator: ManualActivator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = Session()
        Log.i(
            TAG,
            "session=${session.id} llm=${llmClient.javaClass.simpleName} " +
                "screen=${screenReader.javaClass.simpleName} " +
                "stt=${speechRecognizer.javaClass.simpleName} " +
                "tts=${ttsEngine.javaClass.simpleName} " +
                "activator=${activator.javaClass.simpleName}",
        )

        // Collect activations and run the pipeline on each one.
        lifecycleScope.launch {
            activator.activations().collect { activation ->
                Log.i(TAG, "activation=$activation")
                runPipelineOnce()
            }
        }

        setContent {
            ReadoutTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    ReadoutHome(
                        padding = padding,
                        onTriggerClick = { manualActivator.trigger() },
                    )
                }
            }
        }
    }

    private suspend fun runPipelineOnce() {
        val snapshot = screenReader.snapshot()
        Log.i(TAG, "snapshot pkg=${snapshot.foregroundPackage} nodes=${snapshot.nodes.size}")
        val answer = llmClient.answer(
            question = "How far have I ridden?",
            screen = snapshot,
            appName = snapshot.foregroundPackage,
        )
        Log.i(TAG, "answer=${answer.text} latencyMs=${answer.latencyMillis}")
        val ttsStart = System.currentTimeMillis()
        runCatching { ttsEngine.speak("Readout initialized") }
            .onSuccess { Log.i(TAG, "tts.speak ok latencyMs=${System.currentTimeMillis() - ttsStart}") }
            .onFailure { Log.w(TAG, "tts.speak failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "Readout"
    }
}

@Composable
private fun ReadoutHome(
    padding: PaddingValues,
    onTriggerClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Readout", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onTriggerClick) {
            Text("Trigger activation")
        }
    }
}
