package com.zcq100.s3browser.ui.buckets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.zcq100.s3browser.AppContainer
import com.zcq100.s3browser.data.Connection
import com.zcq100.s3browser.s3.BucketInfo
import com.zcq100.s3browser.s3.toUserMessage
import com.zcq100.s3browser.ui.common.BottomNav
import com.zcq100.s3browser.ui.common.PrimaryButton
import com.zcq100.s3browser.ui.common.Tab
import com.zcq100.s3browser.ui.common.appViewModel
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BucketsViewModel(private val app: AppContainer) : ViewModel() {
    val connections = app.connections.connections.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val active = app.connections.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var buckets by mutableStateOf<List<BucketInfo>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val publicRead = mutableStateMapOf<String, Boolean>()

    private var loadedFor: Connection? = null
    private var job: Job? = null

    fun load(conn: Connection, force: Boolean = false) {
        if (!force && conn == loadedFor) return
        loadedFor = conn
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            error = null
            if (!force) buckets = emptyList()
            val store = app.storeFor(conn)
            runCatching { store.listBuckets() }
                .onSuccess { list ->
                    buckets = list
                    publicRead.clear()
                    // 公开读标记来自桶策略；查不到（不支持或无权限）就当作私有。
                    list.forEach { b -> launch { publicRead[b.name] = store.isPublicRead(b.name) } }
                }
                .onFailure { buckets = emptyList(); error = it.toUserMessage() }
            loading = false
        }
    }

    private var resumedOnce = false

    /** 从子页面（新建桶、文件浏览）返回时刷新；首次进入由 [load] 负责。 */
    fun onResume() {
        if (resumedOnce) loadedFor?.let { load(it, force = true) }
        resumedOnce = true
    }

    fun switchTo(id: String) = viewModelScope.launch { app.connections.setActive(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketsScreen(
    onOpenBucket: (String) -> Unit,
    onNewBucket: () -> Unit,
    onEditConnection: (String?) -> Unit,
    onTab: (Tab) -> Unit,
) {
    val vm = appViewModel { BucketsViewModel(it) }
    val connections by vm.connections.collectAsState()
    val active by vm.active.collectAsState()
    var sheet by remember { mutableStateOf(false) }

    LaunchedEffect(active) { active?.let { vm.load(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    Scaffold(
        containerColor = Palette.Ground,
        bottomBar = { BottomNav(Tab.Buckets, onTab) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (connections != null && connections!!.isEmpty()) {
                EmptyConnections { onEditConnection(null) }
                return@Column
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                active?.let { ConnectionChip(it) { sheet = true } }
            }
            Text(
                "存储桶", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
                fontSize = 32.sp, fontWeight = FontWeight.Bold,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (vm.loading && vm.buckets.isEmpty()) "加载中…" else "${vm.buckets.size} 个存储桶 · 按名称",
                    Modifier.weight(1f), fontSize = 13.sp, color = Palette.Muted,
                )
                Row(
                    Modifier.height(36.dp).clip(CircleShape).background(Palette.Accent).clickable(onClick = onNewBucket)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("新建桶", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            PullToRefreshBox(
                isRefreshing = vm.loading && vm.buckets.isNotEmpty(),
                onRefresh = { active?.let { vm.load(it, force = true) } },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.error?.let { msg ->
                        item {
                            Text(
                                msg,
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.WarnBg).padding(14.dp),
                                color = Palette.WarnFg, fontSize = 13.sp, lineHeight = 19.sp,
                            )
                        }
                    }
                    items(vm.buckets, key = { it.name }) { b ->
                        BucketRow(b, vm.publicRead[b.name] == true) { onOpenBucket(b.name) }
                    }
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = Palette.Surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text("切换连接", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                connections.orEmpty().forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.switchTo(c.id); sheet = false },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(24.dp)) {
                            if (c.id == active?.id) Icon(Icons.Outlined.Check, "当前连接", tint = Palette.Accent)
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(c.name, fontSize = 15.sp)
                            Text("${c.provider.label} · ${c.host}", fontSize = 12.sp, color = Palette.Muted, fontFamily = Mono)
                        }
                        IconButton(onClick = { sheet = false; onEditConnection(c.id) }) {
                            Icon(Icons.Outlined.Edit, "编辑 ${c.name}", tint = Palette.Muted)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { sheet = false; onEditConnection(null) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, null, tint = Palette.Accent)
                    Text("添加连接", Modifier.padding(start = 8.dp), color = Palette.Accent, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun ConnectionChip(conn: Connection, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).clip(CircleShape).background(Palette.Surface)
            .border(1.dp, Palette.BorderStrong, CircleShape).clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Palette.Ink), contentAlignment = Alignment.Center) {
            Text(conn.name.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(conn.host, Modifier.padding(horizontal = 8.dp), fontFamily = Mono, fontSize = 13.sp, maxLines = 1)
        Icon(Icons.Outlined.ExpandMore, "切换连接", Modifier.size(16.dp))
    }
}

@Composable
private fun BucketRow(b: BucketInfo, isPublic: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Chip), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Storage, null, Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    b.name, Modifier.weight(1f, fill = false), fontFamily = Mono, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (isPublic) {
                    Text(
                        "公开读",
                        Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.WarnBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp, color = Palette.WarnFg,
                    )
                }
            }
            b.createdAt?.let {
                Text("创建于 " + SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(it)), fontSize = 12.sp, color = Palette.Muted)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Palette.Faint)
    }
}

@Composable
private fun EmptyConnections(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有连接", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "添加一个 S3 兼容服务：AWS、MinIO、阿里云 OSS、腾讯云 COS、华为云 OBS、七牛、R2 等。",
            Modifier.padding(top = 8.dp, bottom = 24.dp), color = Palette.Muted, fontSize = 14.sp, lineHeight = 21.sp,
        )
        PrimaryButton("添加连接", onAdd, icon = Icons.Outlined.Add)
    }
}
