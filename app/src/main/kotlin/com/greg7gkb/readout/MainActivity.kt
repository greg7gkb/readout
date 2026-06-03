package com.greg7gkb.readout

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.greg7gkb.readout.audio.SpeechRecognizer
import com.greg7gkb.readout.audio.TtsEngine
import com.greg7gkb.readout.common.di.IoDispatcher
import com.greg7gkb.readout.common.model.Session
import com.greg7gkb.readout.llm.LlmClient
import com.greg7gkb.readout.screen.ScreenReader
import com.greg7gkb.readout.ui.theme.ReadoutTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = Session()
        Log.i(
            TAG,
            "session=${session.id} llm=${llmClient.javaClass.simpleName} " +
                "screen=${screenReader.javaClass.simpleName} " +
                "stt=${speechRecognizer.javaClass.simpleName} " +
                "tts=${ttsEngine.javaClass.simpleName}",
        )
        lifecycleScope.launch {
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
        setContent {
            ReadoutTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Greeting()
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
private fun Greeting() {
    Text(text = "Readout", style = MaterialTheme.typography.headlineMedium)
}
