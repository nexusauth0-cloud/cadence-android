package com.cadence.core.common

/**
 * Central batching mechanism (ADR-0005).
 *
 * All `IN (:ids)` collection-parameter queries MUST go through this wrapper.
 * It de-duplicates items (preserving first occurrence order), chunks into
 * bounded batches, and merges results in order.
 *
 * The 900-item constant is the safe ceiling for Android's bundled SQLite
 * (minSdk 26, SQLite <3.32 caps at 999). Bumping requires an ADR-level edit.
 */
object BatchConfig {
    /** Maximum number of bound variables per single SQL query. */
    const val BATCH_SIZE = 900
}

/**
 * Process [items] in de-duplicated batches of at most [batchSize] items.
 *
 * @param items The collection of items to process (may contain duplicates).
 * @param batchSize Maximum items per batch call. Defaults to [BatchConfig.BATCH_SIZE].
 * @param block The batch processor. Receives a de-duplicated, ordered sublist
 *              and returns results for that batch.
 * @return Merged results from all batches, preserving the order of first occurrence.
 */
suspend fun <T, R> inBatches(
    items: Collection<T>,
    batchSize: Int = BatchConfig.BATCH_SIZE,
    block: suspend (List<T>) -> List<R>,
): List<R> {
    require(batchSize > 0) { "batchSize must be positive" }
    if (items.isEmpty()) return emptyList()

    // De-duplicate preserving first occurrence order
    val seen = mutableSetOf<T>()
    val unique = mutableListOf<T>()
    for (item in items) {
        if (seen.add(item)) {
            unique.add(item)
        }
    }

    // Process in bounded batches
    val results = mutableListOf<R>()
    unique.chunked(batchSize).forEach { chunk ->
        results.addAll(block(chunk))
    }
    return results
}
