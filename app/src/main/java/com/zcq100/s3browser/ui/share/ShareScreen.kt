package com.zcq100.s3browser.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.zcq100.s3browser.AppContainer
import com.zcq100.s3browser.data.Connection
import com.zcq100.s3browser.data.ShareRecord
import com.zcq100.s3browser.s3.toUserMessage
import com.zcq100.s3browser.ui.common.BackButton
import com.zcq100.s3browser.ui.common.Card
import com.zcq100.s3browser.ui.common.FileBadge
import com.zcq100.s3browser.ui.common.Notice
import com.zcq100.s3browser.ui.common.PrimaryButton
import com.zcq100.s3browser.ui.common.RowDivider
import com.zcq100.s3browser.ui.common.SecondaryButton
import com.zcq100.s3browser.ui.common.SectionLabel
import com.zcq100.s3browser.ui.common.Segmented
import com.zcq100.s3browser.ui.common.ToggleRow
import com.zcq100.s3browser.ui.common.appViewModel
import com.zcq100.s3browser.ui.common.copyText
import com.zcq100.s3browser.ui.common.formatSize
import com.zcq100.s3browser.ui.common.formatTime
import com.zcq100.s3browser.ui.common.shareText
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

enum class Expiry(val label: String, val duration: Duration?) {
    H1("1 小时", 1.hours), D1("1 天", 1.days), D7("7 天", 7.days), Custom("自定义", null)
}

class ShareViewModel(private val app: AppContainer, val bucket: String, val key: String) : ViewModel() {
    val active = app.connections.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var size by mutableStateOf<Long?>(null)
        private set
    var expiry by mutableStateOf(Expiry.D7)
        set
    var customHours by mutableStateOf("48")
    var attachment by mutableStateOf(false)
    var generating by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<ShareRecord?>(null)
        private set

    init {
        viewModelScope.launch { size = runCatching { app.active().second.head(bucket, key).size }.getOrNull() }
    }

    val customValid get() = customHours.toIntOrNull()?.let { it in 1..168 } == true

    val ttl: Duration
        get() = expiry.duration ?: (customHours.toIntOrNull()?.coerceIn(1, 168) ?: 1).hours

    fun generate() {
        generating = true
        error = null
        viewModelScope.launch {
            runCatching {
                val (conn, store) = app.active()
                val now = System.currentTimeMillis()
                val ttl = this@ShareViewModel.ttl
                val url = store.presignGet(bucket, key, ttl, attachment)
                ShareRecord(
                    connectionId = conn.id, connectionName = conn.name, bucket = bucket, key = key, url = url,
                    createdAt = now, expiresAt = now + ttl.inWholeMilliseconds, attachment = attachment,
                ).also { app.shares.add(it) }
            }.onSuccess { result = it }.onFailure { error = it.toUserMessage() }
            generating = false
        }
    }
}

@Composable
fun ShareScreen(bucket: String, key: String, onBack: () -> Unit, onDone: () -> Unit, onHistory: () -> Unit) {
    val vm = appViewModel { ShareViewModel(it, bucket, key) }
    val result = vm.result
    if (result == null) ShareForm(vm, onBack) else ShareResult(result, vm.active.value, onDone, onHistory)
}

@Composable
private fun ShareForm(vm: ShareViewModel, onBack: () -> Unit) {
    val conn = vm.active.value
    val name = vm.key.substringAfterLast('/')
    val expiresAt = System.currentTimeMillis() + vm.ttl.inWholeMilliseconds

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("创建分享链接", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Card {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FileBadge(name, false)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(name, fontFamily = Mono, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                (listOf(vm.bucket) + vm.key.split('/').dropLast(1)).joinToString(" / "),
                                vm.size?.let { formatSize(it) },
                            ).joinToString(" · "),
                            fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column {
                SectionLabel("有效期")
                Segmented(Expiry.entries.map { it to it.label }, vm.expiry, { vm.expiry = it })
                if (vm.expiry == Expiry.Custom) {
                    OutlinedTextField(
                        value = vm.customHours,
                        onValueChange = { v -> vm.customHours = v.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        singleLine = true,
                        suffix = { Text("小时") },
                        isError = !vm.customValid,
                        textStyle = TextStyle(fontFamily = Mono, fontSize = 15.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Palette.Surface, unfocusedContainerColor = Palette.Surface,
                            unfocusedBorderColor = Palette.BorderStrong, focusedBorderColor = Palette.Accent,
                        ),
                    )
                }
                Text(
                    "将于 ${formatTime(expiresAt)} 失效 · 最长 7 天（168 小时）",
                    Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Palette.Muted,
                )
            }

            Card {
                ToggleRow("打开即下载", "不在浏览器里预览，直接保存为原文件名", vm.attachment, { vm.attachment = it })
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp)) {
                    Text("签名密钥", Modifier.weight(1f), fontSize = 15.sp)
                    Text(conn?.maskedAccessKey.orEmpty(), fontFamily = Mono, fontSize = 13.sp, color = Palette.TextSecondary)
                }
            }

            Notice("拿到链接的人都能下载。链接发出后不能撤销，也看不到访问次数；要提前作废，只能删除或移动这个文件，或者停用签名密钥。")
            vm.error?.let { Notice(it, warn = true) }
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton(
                "生成链接", vm::generate,
                enabled = conn != null && (vm.expiry != Expiry.Custom || vm.customValid), loading = vm.generating,
            )
        }
    }
}

@Composable
private fun ShareResult(record: ShareRecord, conn: Connection?, onDone: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    val qr = remember(record.url) { qrBitmap(record.url, 600) }

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("完成", color = Palette.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, tint = Color.White)
                }
                Text("链接已生成", Modifier.padding(top = 8.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${record.fileName} · ${formatTime(record.expiresAt)} 失效", fontSize = 14.sp, color = Palette.Muted)
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("预签名链接", fontSize = 12.sp, color = Palette.Muted)
                    Text(
                        record.url, fontFamily = Mono, fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary,
                        maxLines = 5, overflow = TextOverflow.Ellipsis,
                    )
                    Image(
                        qr.asImageBitmap(), "链接二维码",
                        Modifier.align(Alignment.CenterHorizontally).size(200.dp)
                            .border(1.dp, Palette.Border, RoundedCornerShape(8.dp)).padding(8.dp),
                        filterQuality = FilterQuality.None,
                    )
                }
            }

            PrimaryButton("复制链接", { context.copyText("分享链接", record.url, "已复制链接") }, icon = Icons.Outlined.ContentCopy)
            SecondaryButton("通过其他应用发送", { context.shareText(record.url) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Share)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Meta("失效时间", formatTime(record.expiresAt))
                Meta("签名密钥", conn?.maskedAccessKey.orEmpty(), mono = true)
                Meta("打开方式", if (record.attachment) "直接下载" else "浏览器预览")
            }
        }
        TextButton(onClick = onHistory, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)) {
            Text("已存入本机分享记录 ›", color = Palette.TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Meta(label: String, value: String, mono: Boolean = false) {
    Row {
        Text(label, Modifier.width(80.dp), fontSize = 14.sp, color = Palette.Muted)
        Text(value, fontSize = if (mono) 13.sp else 14.sp, fontFamily = if (mono) Mono else null)
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L),
    )
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) 0xFF16181A.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
