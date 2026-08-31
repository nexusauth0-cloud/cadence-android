package com.cadence.android.diskcache

import com.cadence.core.cache.CacheBudget
import com.cadence.core.cache.CacheEntry
import com.cadence.core.cache.CacheTierId
import com.cadence.core.cache.CacheTierStore
import com.cadence.core.cache.EvictionPolicy
import com.cadence.core.cache.TierAccounting
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Persistent [CacheTierStore] (ADR-0007) backed by the app's private file
 * directory. Each entry is a content blob plus a sidecar `.meta` descriptor.
 *
 * On-disk layout, one entry per key:
 *
 *   {root}/{sha256(key).take(16)}          content bytes
 *   {root}/{sha256(key).take(16)}.meta      key=value descriptor
 *
 * The descriptor records size, creation/access time and optional expiry so the
 * LRU/TTL eviction semantics already proven in-memory can be reproduced without
 * holding the full catalog in memory. All directory state is also re-derivable
 * by scanning, so a torn write never corrupts the whole tier.
 *
 * Backups never include this directory: it lives under filesDir, and the host
 * app's dataExtractionRules exclude the cache root (ADR-0004 §3 only concerns
 * credentials, but the cache root is volatile by design and not part of backup).
 */
class FileCacheTierStore(
    override val tierId: CacheTierId,
    private val rootDir: File,
    private val budget: CacheBudget,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : CacheTierStore {

    private val mutex = Mutex()

    init {
        require(tierId == budget.tierId) { "tierId must match budget.tierId" }
    }

    /** Write (or overwrite) [bytes] for [key] with the tier's default policy.
     *  Content bytes are mandatory here; [CacheTierStore.put] records only the
     *  metadata contract used by accounting. */
    override suspend fun put(entry: CacheEntry) = withContext(dispatcher) {
        mutex.withLock {
            writeDescriptor(entry)
        }
    }

    /** Content-aware write: stores [bytes] and the metadata for [entry]. */
    suspend fun write(key: String, bytes: ByteArray, ttlMs: Long? = null): CacheEntry {
        require(bytes.isNotEmpty()) { "cache content must not be empty" }
        val now = System.currentTimeMillis()
        val entry = CacheEntry(
            key = key,
            tierId = tierId,
            sizeBytes = bytes.size.toLong(),
            createdAt = now,
            lastAccessedAt = now,
            expiresAt = ttlMs?.let { now + it },
        )
        withContext(dispatcher) {
            mutex.withLock {
                ensureRootLocked()
                dataFile(key).writeBytes(bytes)
                writeDescriptor(entry)
                if (budget.evictionPolicy is EvictionPolicy.Lru) {
                    evictLruToBudgetLocked()
                }
            }
        }
        return entry
    }

    /** Read the content bytes for [key], bumping the access time. */
    suspend fun readBytes(key: String): ByteArray? = withContext(dispatcher) {
        mutex.withLock {
            val data = dataFile(key)
            if (!data.isFile) return@withLock null
            val entry = readDescriptorLocked(key) ?: return@withLock null
            if (entry.isExpired(System.currentTimeMillis())) {
                deleteEntryLocked(key)
                return@withLock null
            }
            touchDescriptorLocked(key)
            data.readBytes()
        }
    }

    override suspend fun get(key: String): CacheEntry? = withContext(dispatcher) {
        mutex.withLock {
            val entry = readDescriptorLocked(key) ?: return@withLock null
            if (entry.isExpired(System.currentTimeMillis())) {
                deleteEntryLocked(key)
                null
            } else {
                touchDescriptorLocked(key)
                entry
            }
        }
    }

    override suspend fun remove(key: String): Boolean = withContext(dispatcher) {
        mutex.withLock {
            deleteEntryLocked(key)
        }
    }

    override suspend fun clear() = withContext(dispatcher) {
        mutex.withLock {
            rootDir.listFiles()?.forEach { it.delete() }
        }
        Unit
    }

    override suspend fun accounting(): TierAccounting = withContext(dispatcher) {
        mutex.withLock {
            val files = rootDir.listFiles()?.filter { it.isFile && it.extension != META_EXT } ?: emptyList()
            val used = files.sumOf { it.length() }
            TierAccounting(
                tierId = tierId,
                entryCount = files.size,
                usedBytes = used,
                budgetBytes = budget.maxBytes,
            )
        }
    }

    override suspend fun pruneExpired(): Int = withContext(dispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val expired = rootDir.listFiles()
                ?.filter { it.isFile && it.extension == META_EXT }
                ?.mapNotNull { parseDescriptor(it) }
                ?.filter { it.isExpired(now) }
                ?: emptyList()
            expired.forEach { deleteEntryLocked(it.key) }
            expired.size
        }
    }

    override suspend fun evictToBudget(): Long = withContext(dispatcher) {
        mutex.withLock { evictLruToBudgetLocked() }
    }

    override suspend fun entries(): List<CacheEntry> = withContext(dispatcher) {
        mutex.withLock {
            rootDir.listFiles()
                ?.filter { it.isFile && it.extension == META_EXT }
                ?.mapNotNull { parseDescriptor(it) }
                ?: emptyList()
        }
    }

    private fun evictLruToBudgetLocked(): Long {
        val maxBytes = when (val p = budget.evictionPolicy) {
            is EvictionPolicy.Lru -> p.maxBytes
            else -> budget.maxBytes
        }
        if (maxBytes < 0) return 0L // unlimited
        var freed = 0L
        // Evict least-recently-used entries until the tier is under budget.
        while (true) {
            val used = rootDir.listFiles()?.filter { it.isFile && it.extension != META_EXT }?.sumOf { it.length() }
                ?: 0L
            if (used <= maxBytes) break
            val lru = rootDir.listFiles()
                ?.filter { it.isFile && it.extension == META_EXT }
                ?.mapNotNull { parseDescriptor(it) }
                ?.minByOrNull { it.lastAccessedAt }
                ?: break
            freed += lru.sizeBytes
            deleteEntryLocked(lru.key)
        }
        return freed
    }

    private fun writeDescriptor(entry: CacheEntry) {
        val props = Properties().apply {
            setProperty(PROP_KEY, entry.key)
            setProperty(PROP_TIER, entry.tierId.name)
            setProperty(PROP_SIZE, entry.sizeBytes.toString())
            setProperty(PROP_CREATED, entry.createdAt.toString())
            setProperty(PROP_ACCESSED, entry.lastAccessedAt.toString())
            setProperty(PROP_EXPIRES, (entry.expiresAt ?: 0L).toString())
        }
        ensureRootLocked()
        metaFile(entry.key).outputStream().use { props.store(it, null) }
    }

    private fun readDescriptorLocked(key: String): CacheEntry? = parseDescriptor(metaFile(key))

    private fun touchDescriptorLocked(key: String) {
        val file = metaFile(key)
        val entry = parseDescriptor(file) ?: return
        writeDescriptor(entry.copy(lastAccessedAt = System.currentTimeMillis()))
    }

    private fun parseDescriptor(file: File): CacheEntry? {
        if (!file.isFile) return null
        val props = Properties()
        runCatching { file.inputStream().use { props.load(it) } }.getOrNull() ?: return null
        val key = props.getProperty(PROP_KEY) ?: return null
        val tier = runCatching { CacheTierId.valueOf(props.getProperty(PROP_TIER, "")) }.getOrNull() ?: return null
        val size = props.getProperty(PROP_SIZE)?.toLongOrNull() ?: return null
        val created = props.getProperty(PROP_CREATED)?.toLongOrNull() ?: return null
        val accessed = props.getProperty(PROP_ACCESSED)?.toLongOrNull() ?: created
        val expires = props.getProperty(PROP_EXPIRES)?.toLongOrNull() ?: 0L
        return CacheEntry(
            key = key,
            tierId = tier,
            sizeBytes = size,
            createdAt = created,
            lastAccessedAt = accessed,
            expiresAt = if (expires > 0L) expires else null,
        )
    }

    private fun deleteEntryLocked(key: String): Boolean {
        val deletedData = dataFile(key).let { if (it.isFile) it.delete() else false }
        val deletedMeta = metaFile(key).let { if (it.isFile) it.delete() else false }
        return deletedData || deletedMeta
    }

    private fun ensureRootLocked() {
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw IOException("cannot create cache tier dir ${rootDir.path}")
        }
    }

    private fun dataFile(key: String): File = File(rootDir, "${hash(key)}")

    private fun metaFile(key: String): File = File(rootDir, "${hash(key)}.meta")

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val META_EXT = "meta"
        const val PROP_KEY = "key"
        const val PROP_TIER = "tier"
        const val PROP_SIZE = "size"
        const val PROP_CREATED = "created"
        const val PROP_ACCESSED = "accessed"
        const val PROP_EXPIRES = "expires"
    }
}
