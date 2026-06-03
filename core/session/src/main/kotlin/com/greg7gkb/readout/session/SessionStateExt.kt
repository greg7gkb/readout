package com.greg7gkb.readout.session

/** Short, glanceable form for notifications, status bars, talkback announcements. */
fun SessionState.summary(): String = when (this) {
    SessionState.Idle -> "Idle"
    is SessionState.Listening -> "Listening…"
    is SessionState.Thinking -> "Thinking…"
    is SessionState.Speaking -> "Speaking…"
    is SessionState.Error -> "Error: $message"
}
