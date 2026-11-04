package com.zcq100.s3browser.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zcq100.s3browser.s3.ObjectDetail
import com.zcq100.s3browser.s3.ObjectEntry
import com.zcq100.s3browser.ui.common.FileBadge
import com.zcq100.s3browser.ui.common.PrimaryButton
import com.zcq100.s3browser.ui.common.copyText
import com.zcq100.s3browser.ui.common.formatSize
import com.zcq100.s3browser.ui.common.formatTime
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsSheet(
    bucket: String,
    entry: ObjectEntry,
    detail: ObjectDetail?,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val path = "s3://$bucket/${entry.key}"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FileBadge(entry.name, false, size = 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(entry.name, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    Text(
                        listOfNotNull(detail?.contentType, formatSize(detail?.size ?: entry.size)).joinToString(" · "),
                        fontSize = 13.sp, color = Palette.Muted,
                    )
                    formatTime(detail?.lastModified ?: entry.lastModified).takeIf { it.isNotEmpty() }?.let {
                        Text("$it 修改", fontSize = 13.sp, color = Palette.Muted)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Ground).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoRow("路径", path)
                InfoRow("ETag", detail?.eTag ?: "…")
                InfoRow("存储类型", detail?.storageClass ?: "…", mono = false)
            }

            PrimaryButton("创建分享链接", onShare, icon = Icons.Outlined.Link)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Outlined.Download, "下载") { onDownload(); onDismiss() }
                Tile(Icons.Outlined.ContentCopy, "复制路径") { context.copyText("路径", path, "已复制路径") }
                Tile(Icons.Outlined.Edit, "重命名", onClick = onRename)
                Tile(Icons.Outlined.Delete, "删除", Palette.Danger, onDelete)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = true) {
    Row {
        Text(label, Modifier.width(64.dp), fontSize = 13.sp, color = Palette.Muted)
        Text(value, Modifier.weight(1f), fontSize = if (mono) 12.sp else 13.sp, fontFamily = if (mono) Mono else null)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(
    icon: ImageVector, label: String, color: Color = Palette.Ink, onClick: () -> Unit,
) {
    Column(
        Modifier.weight(1f).heightIn(min = 68.dp).clip(RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
        Text(label, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = color)
    }
}
