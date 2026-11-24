package com.zcq100.s3browser.ui.files

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcq100.s3browser.AppContainer
import com.zcq100.s3browser.s3.ObjectDetail
import com.zcq100.s3browser.s3.ObjectEntry
import com.zcq100.s3browser.s3.ObjectStore
import com.zcq100.s3browser.s3.toUserMessage
import com.zcq100.s3browser.ui.preview.mediaMimeType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

private const val TAG = "S3Files"

class FilesViewModel(private val app: AppContainer, val bucket: String) : ViewModel() {
    var prefix by mutableStateOf("")
        private set
    var query by mutableStateOf("")
        private set
    var entries by mutableStateOf<List<ObjectEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selecting by mutableStateOf(false)
        private set
    val selected = mutableStateMapOf<String, ObjectEntry>()

    /** 进行中的上传/删除等操作，显示在底部。 */
    var busy by mutableStateOf<String?>(null)
        private set

    /** 一次性提示（Toast）。 */
    var message by mutableStateOf<String?>(null)

    var detailEntry by mutableStateOf<ObjectEntry?>(null)
        private set
    var detail by mutableStateOf<ObjectDetail?>(null)
        private set

    private var nextToken: String? = null
    private var listJob: Job? = null

    init {
        reload()
    }

    private suspend fun store(): ObjectStore = app.active().second

    val crumbs: List<String> get() = prefix.split('/').filter { it.isNotEmpty() }

    fun open(newPrefix: String) {
        prefix = newPrefix
        query = ""
        exitSelect()
        reload()
    }

    /** 返回上一级；已经在根目录时返回 false。 */
    fun up(): Boolean {
        if (prefix.isEmpty()) return false
        open(crumbs.dropLast(1).joinToString("") { "$it/" })
        return true
    }

    fun search(text: String) {
        query = text
        listJob?.cancel()
        listJob = viewModelScope.launch {
            delay(300)
            doLoad()
        }
    }

    fun reload() {
        listJob?.cancel()
        listJob = viewModelScope.launch { doLoad() }
    }

    private suspend fun doLoad() {
        loading = true
        error = null
        runCatching { store().list(bucket, prefix + query, null) }
            .onSuccess { entries = it.entries; nextToken = it.nextToken }
            .onFailure { entries = emptyList(); nextToken = null; error = it.toUserMessage() }
        loading = false
    }

    fun loadMore() {
        val token = nextToken ?: return
        if (loadingMore || loading) return
        loadingMore = true
        viewModelScope.launch {
            runCatching { store().list(bucket, prefix + query, token) }
                .onSuccess { page -> entries = entries + page.entries; nextToken = page.nextToken }
                .onFailure { message = it.toUserMessage() }
            loadingMore = false
        }
    }

    val hasMore get() = nextToken != null

    // ---------- 选择 ----------

    fun toggleSelect() {
        if (selecting) exitSelect() else selecting = true
    }

    fun exitSelect() {
        selecting = false
        selected.clear()
    }

    fun toggle(entry: ObjectEntry) {
        if (selected.remove(entry.key) == null) selected[entry.key] = entry
    }

    // ---------- 详情 ----------

    fun showDetail(entry: ObjectEntry) {
        detailEntry = entry
        detail = null
        viewModelScope.launch {
            detail = runCatching { store().head(bucket, entry.key) }.getOrNull()
        }
    }

    fun hideDetail() {
        detailEntry = null
        detail = null
    }

    // ---------- 操作 ----------

    private fun runOp(label: String, done: String, block: suspend (ObjectStore) -> Unit) {
        busy = label
        viewModelScope.launch {
            runCatching { block(store()) }
                .onSuccess { message = done; reload() }
                .onFailure { message = it.toUserMessage() }
            busy = null
        }
    }

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = app.context.contentResolver
        busy = "准备上传…"
        viewModelScope.launch {
            var ok = 0
            var failure: String? = null
            uris.forEachIndexed { i, uri ->
                var name = "upload-${System.currentTimeMillis()}"
                var size = -1L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
                busy = "上传 ${i + 1}/${uris.size}：$name"
                runCatching {
                    if (size < 0) error("无法读取文件大小：$name")
                    store().put(bucket, prefix + name, size, resolver.getType(uri)) {
                        resolver.openInputStream(uri) ?: error("无法打开文件：$name")
                    }
                }.onSuccess { ok++ }.onFailure { Log.w(TAG, "upload $name failed", it); failure = it.toUserMessage() }
            }
            busy = null
            message = failure?.let { "已上传 $ok/${uris.size} 个，失败：$it" } ?: "已上传 $ok 个文件"
            reload()
        }
    }

    fun createFolder(name: String) {
        val clean = name.trim().trim('/')
        if (clean.isEmpty()) return
        runOp("新建文件夹…", "已新建文件夹") { it.createFolder(bucket, prefix + clean + "/") }
    }

    fun delete(keys: List<String>) {
        hideDetail()
        exitSelect()
        runOp("删除 ${keys.size} 个文件…", "已删除 ${keys.size} 个文件") { it.delete(bucket, keys) }
    }

    fun rename(key: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains('/')) return
        val target = key.substringBeforeLast('/', "").let { if (it.isEmpty()) clean else "$it/$clean" }
        if (target == key) return
        hideDetail()
        runOp("重命名…", "已重命名") { it.rename(bucket, key, target) }
    }

    /** 用 1 小时有效的预签名链接交给系统下载管理器，下载不经过应用进程。 */
    fun download(entries: List<ObjectEntry>) {
        viewModelScope.launch {
            runCatching {
                val store = store()
                val dm = app.context.getSystemService(DownloadManager::class.java)
                entries.forEach { e ->
                    val url = store.presignGet(bucket, e.key, 1.hours, attachment = true)
                    dm.enqueue(
                        DownloadManager.Request(Uri.parse(url))
                            .setTitle(e.name)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "S3/${e.name}"),
                    )
                }
            }.onSuccess { message = "已开始下载，保存在 下载/S3" }.onFailure { message = it.toUserMessage() }
        }
    }

    /**
     * 视频/音频不做内置播放器：签一个预签名链接，交给系统里已装的播放器或浏览器。
     * 播放器会自己发起 Range 请求，链接有效期取签名上限，避免长视频播到一半失效。
     */
    fun openExternally(entry: ObjectEntry) {
        viewModelScope.launch {
            busy = "准备打开…"
            runCatching {
                val url = store().presignGet(bucket, entry.key, ObjectStore.MAX_PRESIGN_TTL, attachment = false)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(url), mediaMimeType(entry.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Intent.EXTRA_TITLE, entry.name)
                app.context.startActivity(Intent.createChooser(intent, "打开 ${entry.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                message = if (it is ActivityNotFoundException) "没有能打开这种文件的应用" else it.toUserMessage()
            }
            busy = null
        }
    }
}
