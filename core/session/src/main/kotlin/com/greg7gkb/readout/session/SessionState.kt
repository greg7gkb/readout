package com.greg7gkb.readout.session

import com.greg7gkb.readout.common.model.Session

/** What the orchestrator is currently doing. UI observes this for live updates. */
sealed interface SessionState {

    data object Idle : SessionState

    data class Listening(val session: Session) : SessionState

    data class Thinking(val session: Session, val question: String) : SessionState

    data class Speaking(val session: Session, val answer: String) : SessionState

    data class Error(val session: Session, val message: String) : SessionState
}
