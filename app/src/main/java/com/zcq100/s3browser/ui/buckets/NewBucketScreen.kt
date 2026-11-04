package com.zcq100.s3browser.ui.buckets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcq100.s3browser.AppContainer
import com.zcq100.s3browser.data.Feature
import com.zcq100.s3browser.s3.NewBucketOptions
import com.zcq100.s3browser.s3.toUserMessage
import com.zcq100.s3browser.ui.common.Card
import com.zcq100.s3browser.ui.common.Notice
import com.zcq100.s3browser.ui.common.PrimaryButton
import com.zcq100.s3browser.ui.common.RowDivider
import com.zcq100.s3browser.ui.common.SectionLabel
import com.zcq100.s3browser.ui.common.Segmented
import com.zcq100.s3browser.ui.common.ToggleRow
import com.zcq100.s3browser.ui.common.appViewModel
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val BUCKET_NAME = Regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")

class NewBucketViewModel(private val app: AppContainer) : ViewModel() {
    val active = app.connections.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var name by mutableStateOf("")
    var publicRead by mutableStateOf(false)
    var versioning by mutableStateOf(false)
        private set
    var objectLock by mutableStateOf(false)
        private set
    var creating by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val nameValid get() = BUCKET_NAME.matches(name) && ".." !in name

    fun changeVersioning(on: Boolean) {
        versioning = on
        if (!on) objectLock = false
    }

    fun changeObjectLock(on: Boolean) {
        objectLock = on
        if (on) versioning = true
    }

    fun create(onDone: () -> Unit) {
        creating = true
        error = null
        viewModelScope.launch {
            runCatching {
                val (_, store) = app.active()
                store.createBucket(NewBucketOptions(name, publicRead, versioning, objectLock))
            }.onSuccess { onDone() }.onFailure { error = it.toUserMessage() }
            creating = false
        }
    }
}

@Composable
fun NewBucketScreen(onClose: () -> Unit, onCreated: () -> Unit) {
    val vm = appViewModel { NewBucketViewModel(it) }
    val conn by vm.active.collectAsState()
    val features = conn?.provider?.features.orEmpty()

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("取消", color = Palette.TextSecondary, fontSize = 15.sp) }
            Text("新建存储桶", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Box(Modifier.padding(horizontal = 28.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column {
                SectionLabel("桶名称")
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { vm.name = it.lowercase().trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 16.sp),
                    trailingIcon = if (vm.nameValid) {
                        { Icon(Icons.Outlined.Check, "名称格式正确", tint = Palette.Success) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Palette.Surface, unfocusedContainerColor = Palette.Surface,
                        unfocusedBorderColor = Palette.BorderStrong, focusedBorderColor = Palette.Accent,
                    ),
                )
                Text(
                    "3–63 位小写字母、数字、连字符或点；创建后不可修改",
                    Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Palette.Muted,
                )
            }

            conn?.let { c ->
                Column {
                    SectionLabel("区域")
                    Text(
                        c.region.ifBlank { "由 Endpoint 决定" } + " · " + c.name,
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Chip)
                            .padding(horizontal = 14.dp, vertical = 15.dp),
                        fontFamily = Mono, fontSize = 15.sp, color = Palette.TextSecondary,
                    )
                    Text("新桶建在当前连接的区域；要换区域，请添加对应区域的连接。", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Palette.Muted)
                }
            }

            if (Feature.PublicReadPolicy in features) {
                Column {
                    SectionLabel("访问权限")
                    Segmented(listOf(false to "私有", true to "公开读"), vm.publicRead, { vm.publicRead = it })
                    if (vm.publicRead) {
                        Notice("任何人都能通过 URL 读取桶内所有对象。只放可公开的静态资源。", warn = true, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text("只有持有密钥的账户能访问；对外可通过分享链接发放单个文件。", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Palette.Muted)
                    }
                }
            }

            val showVersioning = Feature.Versioning in features
            val showLock = Feature.ObjectLock in features
            if (showVersioning || showLock) {
                Card {
                    if (showVersioning) {
                        ToggleRow("版本控制", "保留覆盖和删除前的旧版本", vm.versioning, vm::changeVersioning)
                    }
                    if (showVersioning && showLock) RowDivider()
                    if (showLock) {
                        ToggleRow("对象锁定", "防止对象被删除，需开启版本控制，创建后不可关闭", vm.objectLock, vm::changeObjectLock)
                    }
                }
            }

            vm.error?.let { Notice(it, warn = true) }
        }

        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton("创建存储桶", { vm.create(onCreated) }, enabled = vm.nameValid && conn != null, loading = vm.creating)
        }
    }
}
