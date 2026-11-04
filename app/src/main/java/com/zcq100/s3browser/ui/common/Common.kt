package com.zcq100.s3browser.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.zcq100.s3browser.AppContainer
import com.zcq100.s3browser.S3BrowserApp
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as S3BrowserApp
    return viewModel { create(app.container) }
}

// ---------- 格式化 ----------

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}

fun formatTime(millis: Long?): String {
    if (millis == null) return ""
    val now = Calendar.getInstance()
    val t = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == t.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - t.get(Calendar.DAY_OF_YEAR)
    val hm = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))
    return when {
        sameYear && dayDiff == 0 -> "今天 $hm"
        sameYear && dayDiff == 1 -> "昨天 $hm"
        sameYear && dayDiff == -1 -> "明天 $hm"
        sameYear -> SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(millis))
    }
}

fun formatRemaining(expiresAt: Long, now: Long = System.currentTimeMillis()): String {
    val left = expiresAt - now
    if (left <= 0) return SimpleDateFormat("M月d日", Locale.CHINA).format(Date(expiresAt)) + "过期"
    // 向上取整：刚签发的 7 天链接应显示「剩 7 天」而不是 6 天。
    fun ceilDiv(unit: Long) = (left + unit - 1) / unit
    return when {
        left > 86_400_000 -> "剩 ${ceilDiv(86_400_000)} 天"
        left > 3_600_000 -> "剩 ${ceilDiv(3_600_000)} 小时"
        else -> "剩 ${ceilDiv(60_000)} 分钟"
    }
}

// ---------- 系统动作 ----------

fun Context.copyText(label: String, text: String, toast: String = "已复制") {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
}

fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

// ---------- 文件类型标签 ----------

private data class Kind(val bg: Color, val fg: Color)

private val img = Kind(Color(0xFFFDEBDD), Color(0xFF9A3412))
private val vid = Kind(Color(0xFFE0E7FF), Color(0xFF1E3A8A))
private val doc = Kind(Color(0xFFE2E8F0), Color(0xFF1E293B))
private val zip = Kind(Color(0xFFDCFCE7), Color(0xFF14532D))
private val other = Kind(Palette.Chip, Palette.Ink)

private fun kindOf(ext: String): Kind = when (ext.lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "avif" -> img
    "mp4", "mov", "mkv", "avi", "webm", "m4v", "mp3", "m4a", "flac", "wav", "aac" -> vid
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json" -> doc
    "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz" -> zip
    else -> other
}

fun extensionOf(name: String): String =
    name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 && it != name }?.uppercase() ?: "FILE"

@Composable
fun FileBadge(name: String, isFolder: Boolean, size: Dp = 40.dp) {
    val ext = extensionOf(name)
    val k = if (isFolder) other else kindOf(ext)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(k.bg),
        contentAlignment = Alignment.Center,
    ) {
        if (isFolder) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = k.fg, modifier = Modifier.size(22.dp))
        } else {
            Text(ext, color = k.fg, fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

// ---------- 控件 ----------

/** 设计稿里的胶囊分段选择器。 */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(9.dp)) else Modifier)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Palette.Surface else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = Palette.Ink)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.TextSecondary)
}

/** 白底卡片，圆角 14，细边框。 */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (enabled) Palette.Ink else Palette.Muted)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Palette.Muted, lineHeight = 17.sp)
        }
        Box(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Accent, uncheckedTrackColor = Palette.SwitchOff,
                uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Palette.Border))
}

@Composable
fun Notice(text: String, warn: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (warn) Palette.WarnBg else Palette.Chip).padding(horizontal = 14.dp, vertical = 12.dp),
        fontSize = 12.sp, lineHeight = 19.sp, color = if (warn) Palette.WarnFg else Palette.TextSecondary,
    )
}
