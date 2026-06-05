package com.greg7gkb.readout.llm

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * One retry on transient failures — IOException (connection reset, DNS hiccup,
 * timeout) or HTTP 5xx. Other 4xx errors aren't retried; they signal a request
 * problem the next attempt will hit identically.
 *
 * Backoff is a single fixed delay rather than exponential — at one retry there's
 * no curve to exponentiate. 500ms is long enough for a transient blip to clear,
 * short enough not to blow the 3-second budget.
 */
internal suspend fun OkHttpClient.executeWithRetry(
    request: Request,
    backoffMillis: Long = 500L,
): Response {
    val first = runCatching { newCall(request).execute() }
    first.getOrNull()?.let { response ->
        if (response.code < 500) return response
        response.close()
    }
    val firstError = first.exceptionOrNull()
    if (firstError != null && firstError !is IOException) throw firstError
    delay(backoffMillis)
    return newCall(request).execute()
}
