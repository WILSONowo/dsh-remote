package com.dsh.mobile.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.decodeFromStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** 用户消息图片内容块（与 UI 的 UserImage 互转） */
@Serializable
data class CachedImage(
    val mediaType: String,
    val base64: String,
)

/** 会话历史缓存的单条条目（与 UI 的 ChatItem 互转） */
@Serializable
data class CachedItem(
    val kind: String,
    val text: String = "",
    val name: String = "",
    val args: String = "",
    val status: String = "",
    val result: String? = null,
    val isError: Boolean = false,
    val thinkSeconds: Long? = null,
    /** S5：思考链文本——reasoning-delta 流式累积；落盘缓存，冷启动旧消息恢复思考区（旧缓存无此字段，默认为空串） */
    val thinkingText: String = "",
    val streaming: Boolean = false,
    /** 服务端消息序号：缓存与网络首屏合并去重用（旧缓存无此字段，默认为 null） */
    val seq: Long? = null,
    /**
     * 用户消息图片：只在内存热缓存里保留完整 base64；磁盘写盘前剥离
     * （磁盘只留 hasImages 占位，冷启动重进先占位、网络刷新后补齐，控制缓存体积）。
     */
    val images: List<CachedImage> = emptyList(),
    val hasImages: Boolean = false,
)

@Serializable
data class CachedHistory(
    val savedAt: Long,
    val items: List<CachedItem>,
    /**
     * 完整加载水位（seq 增量方案）：loadAll 翻到底时记录的最老 seq。
     * 非空 = items 覆盖 [completeThroughSeq, 最新] 全区间，重进会话免翻页直接全量秒开；
     * 旧格式缓存缺该字段 → null（退化为普通尾部缓存）。
     */
    val completeThroughSeq: Long? = null,
)

/**
 * 进程级内存热缓存：解码结果 LRU，避免返回会话/首页时重复读盘+解压+JSON 解码。
 * - 历史：最近 8 个会话的解码列表（LRU）
 * - 会话列表：最近一次解码结果
 * - 预取槽：Home 后台预取的会话首屏（HistoryValue 原始对象，点开即消费）
 */
object HistoryMemoryCache {
    private const val MAX_HISTORIES = 8

    private val histories = object : LinkedHashMap<String, CachedHistory>(MAX_HISTORIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedHistory>): Boolean =
            size > MAX_HISTORIES
    }
    private val prefetched = HashMap<String, HistoryValue>()
    private val listCache = object : LinkedHashMap<String, List<SessionSummary>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SessionSummary>>) = size > 8
    }

    @Synchronized fun history(id: String): List<CachedItem>? = histories[id]?.items
    @Synchronized fun historyWithMeta(id: String): CachedHistory? = histories[id]
    @Synchronized fun putHistory(id: String, items: List<CachedItem>, completeThroughSeq: Long? = null) {
        if (items.isNotEmpty()) histories[id] = CachedHistory(0, items, completeThroughSeq)
    }
    @Synchronized fun sessionList(scope: String = "legacy"): List<SessionSummary>? = listCache[scope]
    @Synchronized fun putSessionList(items: List<SessionSummary>?, scope: String = "legacy") {
        if (items == null) listCache.remove(scope) else listCache[scope] = items
    }
    @Synchronized fun clearHistory(id: String) { histories.remove(id); prefetched.remove(id) }
    /** 取走预取（一次性消费）；无则返回 null */
    @Synchronized fun takePrefetch(id: String): HistoryValue? = prefetched.remove(id)
    @Synchronized fun putPrefetch(id: String, value: HistoryValue) {
        if (value.events.isEmpty()) return
        if (prefetched.size >= 16) prefetched.clear()
        prefetched[id] = value
    }
}

/**
 * 冷热分离缓存层：
 * - 热：内存中最近条目（UI 状态流）
 * - 温：本文件 —— gzip 压缩的磁盘缓存（历史和列表均按电脑隔离），秒开秒显
 * - 冷：服务器全量历史（loadMore 分页）
 * gzip 同时服务于"传输减少"：缓存命中时零网络；未命中时 OkHttp 已对响应启用透明 gzip。
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class HistoryCache internal constructor(private val cacheRoot: File, private val scope: String) {
    constructor(context: Context, scope: String = "legacy") : this(context.cacheDir, scope)
    private val directory = if (scope == "legacy") cacheRoot else File(cacheRoot, "computers/${cacheKey(scope)}")
    fun memoryKey(sessionId: String): String = "$scope:${cacheKey(sessionId)}"
    fun hasHistory(sessionId: String): Boolean = HistoryMemoryCache.history(memoryKey(sessionId)) != null
    fun putPrefetch(sessionId: String, value: HistoryValue) = HistoryMemoryCache.putPrefetch(memoryKey(sessionId), value)
    fun takePrefetch(sessionId: String): HistoryValue? = HistoryMemoryCache.takePrefetch(memoryKey(sessionId))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val historyDir = File(directory, "history")
    private val listFile = File(directory, "session-list.json.gz")

    // ── 会话历史 ──

    fun loadHistory(sessionId: String): List<CachedItem>? = loadHistoryWithMeta(sessionId)?.items

    /** 读缓存并携带完整加载水位（loadAll 的 seq 增量标记）。 */
    fun loadHistoryWithMeta(sessionId: String): CachedHistory? {
        // 内存热缓存优先（零解码）；未命中才读盘解压（完整水位与条目一起缓存）
        HistoryMemoryCache.historyWithMeta(memoryKey(sessionId))?.let { return it }
        val f = historyFile(sessionId)
        if (!f.exists() || f.length() > 8 * 1024 * 1024) return null
        val doc = runCatching {
            GZIPInputStream(f.inputStream()).use {
                json.decodeFromStream<CachedHistory>(it)
            }
        }.getOrNull() ?: return null
        HistoryMemoryCache.putHistory(memoryKey(sessionId), doc.items, doc.completeThroughSeq)
        return doc
    }

    fun saveHistory(sessionId: String, items: List<CachedItem>, completeThroughSeq: Long? = null) {
        if (items.isEmpty()) return
        // 内存即时生效（返回再进零解码，含图片 base64），磁盘延迟由调用方防抖控制
        HistoryMemoryCache.putHistory(memoryKey(sessionId), items, completeThroughSeq)
        // 温缓存截断：仅普通尾部缓存限 120 条（省空间）；完整水位非空 = 覆盖到最老，
        // 截断会破坏 [completeThroughSeq, 最新] 区间完整性 → 全量落盘（图片 base64 仍剥离）
        val capped = (if (completeThroughSeq != null) items else items.takeLast(120)).map {
            if (it.images.isNotEmpty()) it.copy(images = emptyList(), hasImages = true) else it
        }
        runCatching {
            historyDir.mkdirs()
            val temp = File.createTempFile("history-", ".tmp", historyDir)
            try {
                GZIPOutputStream(temp.outputStream().buffered()).use {
                    json.encodeToStream(CachedHistory.serializer(), CachedHistory(System.currentTimeMillis(), capped, completeThroughSeq), it)
                }
                if (temp.length() > 8L * 1024 * 1024) {
                    historyFile(sessionId).delete()
                    return
                }
                java.nio.file.Files.move(temp.toPath(), historyFile(sessionId).toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                trimDiskCache()
            } finally {
                temp.delete()
            }
        }
    }

    private fun cacheFiles(): List<File> = buildList {
        fun collect(dir: File) {
            File(dir, "history").listFiles()?.filterTo(this) { it.isFile && it.name.endsWith(".json.gz") }
            File(dir, "session-list.json.gz").takeIf { it.isFile }?.let { add(it) }
        }
        collect(cacheRoot)
        File(cacheRoot, "computers").listFiles()?.filter { it.isDirectory }?.forEach { collect(it) }
    }

    internal fun trimDiskCache(budget: Long = 32L * 1024 * 1024) {
        val files = cacheFiles()
        var bytes = files.sumOf { it.length() }
        if (bytes <= budget) return
        for (file in files.sortedBy { it.lastModified() }) {
            val size = file.length()
            if (file.delete()) bytes -= size
            if (bytes <= budget) break
        }
    }

    fun prune(ttlDays: Int = 7, listTtlDays: Int = 1) {
        val now = System.currentTimeMillis()
        cacheFiles().forEach { file ->
            val ttl = if (file.name == "session-list.json.gz") listTtlDays else ttlDays
            if (now - file.lastModified() > ttl * 86_400_000L) file.delete()
        }
        trimDiskCache()
    }

    fun clearHistory(sessionId: String) {
        historyFile(sessionId).delete()
        HistoryMemoryCache.clearHistory(memoryKey(sessionId))
    }

    // ── 会话列表（按电脑隔离，短 TTL 语义由调用方控制） ──

    fun loadSessionList(): List<SessionSummary>? {
        // 内存热缓存优先
        HistoryMemoryCache.sessionList(scope)?.let { return it }
        if (!listFile.exists() || listFile.length() > 1 * 1024 * 1024) return null
        val items = runCatching {
            GZIPInputStream(listFile.inputStream()).use {
                json.decodeFromStream<SessionListValue>(it).items
            }
        }.getOrNull()
        if (items != null) HistoryMemoryCache.putSessionList(items, scope)
        return items
    }

    fun saveSessionList(items: List<SessionSummary>) {
        HistoryMemoryCache.putSessionList(items, scope)
        runCatching {
            directory.mkdirs()
            val temp = File.createTempFile("list-", ".tmp", directory)
            try {
                GZIPOutputStream(temp.outputStream().buffered()).use {
                    json.encodeToStream(SessionListValue.serializer(), SessionListValue(items), it)
                }
                java.nio.file.Files.move(temp.toPath(), listFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                trimDiskCache()
            } finally { temp.delete() }
        }
    }

    private fun historyFile(sessionId: String): File =
        File(historyDir, cacheKey(sessionId) + ".json.gz")
}
