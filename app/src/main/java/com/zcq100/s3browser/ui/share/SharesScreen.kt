package com.zcq100.s3browser.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcq100.s3browser.AppContainer
import com.zcq100.s3browser.data.ShareRecord
import com.zcq100.s3browser.ui.common.BottomNav
import com.zcq100.s3browser.ui.common.FileBadge
import com.zcq100.s3browser.ui.common.SecondaryButton
import com.zcq100.s3browser.ui.common.Segmented
import com.zcq100.s3browser.ui.common.Tab
import com.zcq100.s3browser.ui.common.appViewModel
import com.zcq100.s3browser.ui.common.copyText
import com.zcq100.s3browser.ui.common.formatRemaining
import com.zcq100.s3browser.ui.common.formatTime
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SharesViewModel(private val app: AppContainer) : ViewModel() {
    val records = app.shares.records.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun delete(id: String) = viewModelScope.launch { app.shares.delete(id) }

    /** 重新生成要用签发时的那个连接，所以先切过去。 */
    fun regenerate(r: ShareRecord, then: () -> Unit) = viewModelScope.launch {
        app.connections.setActive(r.connectionId)
        then()
    }
}

@Composable
fun SharesScreen(onTab: (Tab) -> Unit, onRegenerate: (bucket: String, key: String) -> Unit) {
    val vm = appViewModel { SharesViewModel(it) }
    val records by vm.records.collectAsState()
    var activeTab by rememberSaveable { mutableStateOf(true) }
    val now = System.currentTimeMillis()
    val (valid, expired) = records.partition { it.expiresAt > now }
    val shown = if (activeTab) valid else expired
    val context = LocalContext.current

    Scaffold(containerColor = Palette.Ground, bottomBar = { BottomNav(Tab.Shares, onTab) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 36.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("分享记录", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("只保存在这台手机上；删除记录不会让链接失效", fontSize = 13.sp, color = Palette.Muted)
                }
                Segmented(listOf(true to "有效 ${valid.size}", false to "已过期 ${expired.size}"), activeTab, { activeTab = it })
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (activeTab) "这里还没有记录。在文件上点「创建分享链接」后会出现在这里。" else "没有过期的链接",
                            Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
                            textAlign = TextAlign.Center, color = Palette.Muted, fontSize = 14.sp,
                        )
                    }
                }
                items(shown, key = { it.id }) { r ->
                    ShareCard(
                        r, isValid = r.expiresAt > now,
                        onCopy = { context.copyText("分享链接", r.url, "已复制链接") },
                        onRegenerate = { vm.regenerate(r) { onRegenerate(r.bucket, r.key) } },
                        onDelete = { vm.delete(r.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareCard(r: ShareRecord, isValid: Boolean, onCopy: () -> Unit, onRegenerate: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileBadge(r.fileName, false, size = 36.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(r.fileName, fontFamily = Mono, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (listOf(r.bucket) + r.key.split('/').dropLast(1)).joinToString(" / ") + " · " + r.connectionName,
                    fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                formatRemaining(r.expiresAt),
                Modifier.clip(RoundedCornerShape(6.dp)).background(if (isValid) Palette.Chip else Palette.Ground)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isValid) Palette.Ink else Palette.Muted,
            )
            Text("${formatTime(r.createdAt)} 签发", fontSize = 12.sp, color = Palette.TextSecondary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isValid) SecondaryButton("复制链接", onCopy, Modifier.weight(1f), height = 40)
            SecondaryButton("重新生成", onRegenerate, Modifier.weight(1f), height = 40)
            SecondaryButton("删除记录", onDelete, Modifier.weight(1f), height = 40, color = Palette.TextSecondary)
        }
    }
}
