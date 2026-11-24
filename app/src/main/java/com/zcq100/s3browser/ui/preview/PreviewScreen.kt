package com.zcq100.s3browser.ui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcq100.s3browser.AppContainer
import com.zcq100.s3browser.s3.ObjectDetail
import com.zcq100.s3browser.s3.toUserMessage
import com.zcq100.s3browser.ui.common.BackButton
import com.zcq100.s3browser.ui.common.Notice
import com.zcq100.s3browser.ui.common.SecondaryButton
import com.zcq100.s3browser.ui.common.appViewModel
import com.zcq100.s3browser.ui.common.formatSize
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 文本只取开头这么多，避免把几百 MB 的日志拉进内存。 */
private const val TEXT_PREVIEW_LIMIT = 512L * 1024

/** 图片先整个读进来再采样解码；超过这个大小就提示下载后查看。 */
private const val IMAGE_PREVIEW_LIMIT = 32L * 1024 * 1024

class PreviewViewModel(private val app: AppContainer, val bucket: String, val key: String) : ViewModel() {
    val name: String = key.substringAfterLast('/')
    val kind: PreviewKind? = PreviewKind.of(name)

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var detail by mutableStateOf<ObjectDetail?>(null)
        private set

    var lines by mutableStateOf<List<String>>(emptyList())
        private set
    var image by mutableStateOf<Bitmap?>(null)
        private set
    var truncated by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun load() {
        val kind = kind ?: run {
            error = "不支持查看这种文件"
            loading = false
            return
        }
        loading = true
        error = null
        truncated = false
        viewModelScope.launch {
            runCatching {
                val store = app.active().second
                val d = store.head(bucket, key)
                detail = d
                when (kind) {
                    PreviewKind.TEXT -> {
                        val bytes = store.read(bucket, key, TEXT_PREVIEW_LIMIT)
                        val decoded = withContext(Dispatchers.Default) { decodeText(bytes) }
                        if (decoded.looksBinary) {
                            error = "这不是文本文件，下载后查看"
                        } else {
                            lines = withContext(Dispatchers.Default) { toDisplayLines(decoded.text) }
                            truncated = d.size > TEXT_PREVIEW_LIMIT
                        }
                    }
                    PreviewKind.IMAGE -> {
                        if (d.size > IMAGE_PREVIEW_LIMIT) {
                            error = "图片太大（${formatSize(d.size)}），下载后查看"
                        } else {
                            val bytes = store.read(bucket, key, IMAGE_PREVIEW_LIMIT)
                            image = withContext(Dispatchers.Default) { decodeSampled(bytes) }
                                ?: run { error = "无法解析这张图片"; null }
                        }
                    }
                    PreviewKind.MEDIA -> error = "这种文件需要用其他应用打开"
                }
            }.onFailure { error = it.toUserMessage() }
            loading = false
        }
    }
}

@Composable
fun PreviewScreen(bucket: String, key: String, onBack: () -> Unit) {
    val vm = appViewModel { PreviewViewModel(it, bucket, key) }
    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding().navigationBarsPadding()) {
        Header(vm, onBack)
        Box(Modifier.weight(1f)) {
            val problem = vm.error
            val bitmap = vm.image
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Accent)
                problem != null -> ErrorBox(problem, vm::load)
                vm.kind == PreviewKind.TEXT -> TextBody(vm.lines)
                bitmap != null -> ImageBody(bitmap, vm.name)
            }
        }
        if (vm.truncated) {
            Notice(
                "文件较大，只显示前 ${formatSize(TEXT_PREVIEW_LIMIT)}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Header(vm: PreviewViewModel, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(vm.name, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(vm.detail?.contentType, vm.detail?.size?.let(::formatSize)).joinToString(" · "),
                fontSize = 12.sp, color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Notice(message, warn = true)
        SecondaryButton("重试", onRetry, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

@Composable
private fun TextBody(lines: List<String>) {
    SelectionContainer {
        LazyColumn(
            Modifier.fillMaxSize().background(Palette.Surface),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            items(lines) { line ->
                Text(
                    if (line.isEmpty()) " " else line,
                    fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ImageBody(bitmap: Bitmap, name: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF101214))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 6f)
                    scale = next
                    offset = if (next <= 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
        )
    }
}

/** 按最长边约 2048px 采样，原图再大也不会把整张位图解进内存。 */
private fun decodeSampled(bytes: ByteArray, maxDimension: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
