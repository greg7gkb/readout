package com.greg7gkb.readout.llm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime-mutable selector for which [CloudProvider] [CloudLlmClient] routes to.
 *
 * Held in-memory only — no persistence yet. Settings UI (Phase 7) will own
 * persistence; debug commands or the orchestrator may flip it during Step 6
 * validation runs without rebuilding the APK.
 *
 * Default is [CloudProvider.CLAUDE] per the Step 3 decision.
 */
@Singleton
class CloudLlmConfig @Inject constructor() {
    private val _provider = MutableStateFlow(CloudProvider.CLAUDE)
    val provider: StateFlow<CloudProvider> = _provider.asStateFlow()

    fun setProvider(provider: CloudProvider) {
        _provider.value = provider
    }
}
