package com.greg7gkb.readout.common.di

import javax.inject.Qualifier

/**
 * Hilt qualifiers for coroutine dispatchers. Inject as
 * `@IoDispatcher dispatcher: CoroutineDispatcher` so test overrides can swap them.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
