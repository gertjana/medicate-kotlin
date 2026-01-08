package dev.gertjanassies.util

import io.lettuce.core.RedisFuture
import java.util.concurrent.CompletableFuture

/**
 * Test implementation of RedisFuture for mocking Redis async operations.
 * This wrapper allows CompletableFuture to be used as a RedisFuture in tests.
 */
class TestRedisFuture<T>(completableFuture: CompletableFuture<T>) :
    CompletableFuture<T>(), RedisFuture<T> {
    init {
        completableFuture.whenComplete { result, exception ->
            if (exception != null) {
                completeExceptionally(exception)
            } else {
                complete(result)
            }
        }
    }

    override fun getError(): String? = null

    override fun await(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean {
        return try {
            get(timeout, unit)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Creates a successfully completed RedisFuture mock with the given value.
 *
 * @param value The value to be returned by the future
 * @return A completed RedisFuture containing the value
 */
fun <T> createRedisFutureMock(value: T): RedisFuture<T> {
    return TestRedisFuture(CompletableFuture.completedFuture(value))
}

/**
 * Creates a failed RedisFuture mock with the given exception.
 *
 * @param exception The exception to be thrown by the future
 * @return A failed RedisFuture that will throw the exception
 */
fun <T> createFailedRedisFutureMock(exception: Exception): RedisFuture<T> {
    val future = CompletableFuture<T>()
    future.completeExceptionally(exception)
    return TestRedisFuture(future)
}
