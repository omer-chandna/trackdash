package dev.trackdash.core.telemetry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.retryWhen

/** Retries upstream acquisition only. Persistence/consumer failures must still terminate recording. */
fun <T> Flow<T>.recoverAcquisition(
    retryMillis: Long = 2_000,
    onFailure: suspend (Throwable) -> Unit,
): Flow<T> {
    require(retryMillis > 0)
    return onCompletion { cause ->
        if (cause == null) throw IllegalStateException("Source stopped delivering data")
    }.retryWhen { cause, _ ->
        if (cause is CancellationException) false else {
            onFailure(cause)
            delay(retryMillis)
            true
        }
    }
}
