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
import com.greg7gkb.readout.common.di.IoDispatcher
import com.greg7gkb.readout.common.model.ScreenSnapshot
import com.greg7gkb.readout.common.model.Session
import com.greg7gkb.readout.llm.LlmClient
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = Session()
        Log.i(TAG, "session=${session.id} ioDispatcher=$ioDispatcher llm=${llmClient.javaClass.simpleName}")
        lifecycleScope.launch {
            val answer = llmClient.answer(
                question = "How far have I ridden?",
                screen = ScreenSnapshot(
                    foregroundPackage = "stub",
                    timestampMillis = System.currentTimeMillis(),
                    nodes = emptyList(),
                ),
                appName = "stub",
            )
            Log.i(TAG, "answer=${answer.text} latencyMs=${answer.latencyMillis}")
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
