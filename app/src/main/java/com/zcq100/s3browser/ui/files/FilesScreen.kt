package com.zcq100.s3browser.ui.files

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zcq100.s3browser.s3.ObjectEntry
import com.zcq100.s3browser.ui.common.BackButton
import com.zcq100.s3browser.ui.common.FileBadge
import com.zcq100.s3browser.ui.common.appViewModel
import com.zcq100.s3browser.ui.common.formatSize
import com.zcq100.s3browser.ui.common.formatTime
import com.zcq100.s3browser.ui.common.toast
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette

@Composable
fun FilesScreen(bucket: String, onBack: () -> Unit, onShare: (bucket: String, key: String) -> Unit) {
    val vm = appViewModel { FilesViewModel(it, bucket) }
    val context = LocalContext.current
    var newFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<String>?>(null) }
    var renaming by remember { mutableStateOf<ObjectEntry?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.upload(it) }

    LaunchedEffect(vm.message) {
        vm.message?.let { context.toast(it); vm.message = null }
    }
    BackHandler(enabled = vm.selecting || vm.prefix.isNotEmpty()) {
        if (vm.selecting) vm.exitSelect() else vm.up()
    }

    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(vm, onBack)
            FileList(vm, Modifier.weight(1f))
            if (vm.selecting) {
                SelectionBar(
                    vm,
                    onShare = { vm.selected.values.singleOrNull()?.let { onShare(bucket, it.key) } },
                    onDelete = { confirmDelete = vm.selected.keys.toList() },
                )
            }
            vm.busy?.let { BusyBar(it) }
        }

        if (!vm.selecting && vm.busy == null) {
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.height(44.dp).clip(CircleShape).background(Palette.Surface)
                        .border(1.dp, Palette.BorderStrong, CircleShape).clickable { newFolder = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(18.dp))
                    Text("新建文件夹", Modifier.padding(start = 6.dp), fontSize = 14.sp)
                }
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    containerColor = Palette.Accent, contentColor = Color.White, shape = CircleShape,
                    icon = { Icon(Icons.Outlined.Upload, null) },
                    text = { Text("上传", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                )
            }
        }
    }

    vm.detailEntry?.let { entry ->
        FileActionsSheet(
            bucket = bucket,
            entry = entry,
            detail = vm.detail,
            onDismiss = vm::hideDetail,
            onShare = { vm.hideDetail(); onShare(bucket, entry.key) },
            onDownload = { vm.download(listOf(entry)) },
            onRename = { renaming = entry },
            onDelete = { confirmDelete = listOf(entry.key) },
        )
    }

    if (newFolder) {
        TextInputDialog("新建文件夹", "", "创建", onDismiss = { newFolder = false }) { vm.createFolder(it); newFolder = false }
    }
    renaming?.let { e ->
        TextInputDialog("重命名", e.name, "重命名", onDismiss = { renaming = null }) { vm.rename(e.key, it); renaming = null }
    }
    confirmDelete?.let { keys ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除 ${keys.size} 个文件？") },
            text = {
                Text(
                    if (keys.size == 1) keys.first().substringAfterLast('/') + "\n删除后不能恢复（开启了版本控制的桶会保留旧版本）。"
                    else "删除后不能恢复（开启了版本控制的桶会保留旧版本）。",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(keys); confirmDelete = null }) { Text("删除", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun Header(vm: FilesViewModel, onBack: () -> Unit) {
    Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton { if (!vm.up()) onBack() }
            Text(
                vm.bucket, Modifier.weight(1f), fontFamily = Mono, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = vm::toggleSelect) {
                Text(
                    if (vm.selecting) "完成" else "选择", fontSize = 15.sp,
                    color = if (vm.selecting) Palette.Accent else Palette.Ink,
                    fontWeight = if (vm.selecting) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }

        // 路径导航
        Row(
            Modifier.padding(start = 16.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crumb("/", vm.crumbs.isEmpty()) { vm.open("") }
            vm.crumbs.forEachIndexed { i, part ->
                Text(" › ", color = Palette.Muted, fontSize = 13.sp)
                val target = vm.crumbs.take(i + 1).joinToString("") { "$it/" }
                Crumb(part, i == vm.crumbs.lastIndex) { vm.open(target) }
            }
        }

        TextField(
            value = vm.query,
            onValueChange = vm::search,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp).border(1.dp, Palette.BorderStrong, RoundedCornerShape(12.dp)),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Palette.Muted) },
            placeholder = { Text("按前缀搜索", color = Palette.Faint, fontSize = 15.sp) },
            textStyle = TextStyle(fontSize = 15.sp),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Palette.Surface, unfocusedContainerColor = Palette.Surface,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun Crumb(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !current, onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        fontFamily = Mono, fontSize = 13.sp,
        color = if (current) Palette.Ink else Palette.Muted,
        fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
    )
}

@Composable
private fun FileList(vm: FilesViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, vm.entries.size) { if (nearEnd && vm.hasMore) vm.loadMore() }

    val visible = if (vm.selecting) vm.entries.filterNot { it.isFolder } else vm.entries
    val folders = vm.entries.count { it.isFolder }

    Column(modifier) {
        Text(
            when {
                vm.loading -> "加载中…"
                else -> "$folders 个文件夹 · ${vm.entries.size - folders} 个文件" + if (vm.hasMore) "（还有更多）" else ""
            },
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 12.sp, color = Palette.Muted,
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 140.dp),
        ) {
            vm.error?.let { msg ->
                item {
                    Text(
                        msg,
                        Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Palette.WarnBg).padding(14.dp),
                        color = Palette.WarnFg, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                }
            }
            if (!vm.loading && vm.error == null && visible.isEmpty()) {
                item {
                    Text(
                        if (vm.query.isNotEmpty()) "没有以「${vm.query}」开头的文件" else "这里是空的",
                        Modifier.fillMaxWidth().padding(vertical = 48.dp), color = Palette.Muted, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            items(visible, key = { it.key }) { e ->
                EntryRow(
                    e,
                    selecting = vm.selecting,
                    checked = vm.selected.containsKey(e.key),
                    onClick = {
                        when {
                            vm.selecting -> vm.toggle(e)
                            e.isFolder -> vm.open(e.key)
                            else -> vm.showDetail(e)
                        }
                    },
                )
            }
            if (vm.loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: ObjectEntry, selecting: Boolean, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(10.dp))
            .background(if (checked) Palette.Surface else Color.Transparent)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selecting) {
            Icon(
                if (checked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                if (checked) "已选" else "未选",
                tint = if (checked) Palette.Accent else Palette.SwitchOff,
            )
        }
        FileBadge(e.name, e.isFolder)
        Column(Modifier.weight(1f)) {
            Text(e.name + if (e.isFolder) "/" else "", fontFamily = Mono, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!e.isFolder) {
                Text(
                    listOf(formatSize(e.size), formatTime(e.lastModified)).filter { it.isNotEmpty() }.joinToString(" · "),
                    fontSize = 12.sp, color = Palette.Muted,
                )
            }
        }
        if (!selecting) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.Faint)
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(Palette.Border))
}

@Composable
private fun SelectionBar(vm: FilesViewModel, onShare: () -> Unit, onDelete: () -> Unit) {
    val count = vm.selected.size
    val total = vm.selected.values.sumOf { it.size }
    Column(Modifier.fillMaxWidth().background(Palette.Ink).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text("已选 $count 项 · ${formatSize(total)}", Modifier.padding(horizontal = 8.dp), color = Color(0xFFC9CCC4), fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            BarAction(Icons.Outlined.Download, "下载", count > 0, Color.White) { vm.download(vm.selected.values.toList()); vm.exitSelect() }
            BarAction(Icons.Outlined.Link, "分享", count == 1, Color.White, onShare)
            BarAction(Icons.Outlined.Delete, "删除", count > 0, Palette.DangerSoft, onDelete)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarAction(
    icon: ImageVector, label: String, enabled: Boolean, color: Color, onClick: () -> Unit,
) {
    Column(
        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val c = if (enabled) color else color.copy(alpha = 0.35f)
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        Text(label, color = c, fontSize = 12.sp)
    }
}

@Composable
private fun BusyBar(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Ink).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        Text(text, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, textStyle = TextStyle(fontFamily = Mono))
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank() && '/' !in text.trim('/')) {
                Text(confirm, color = Palette.Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = Palette.Surface,
    )
}
