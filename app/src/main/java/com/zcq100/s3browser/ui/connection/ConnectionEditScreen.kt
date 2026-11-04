package com.zcq100.s3browser.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zcq100.s3browser.AppContainer
import com.zcq100.s3browser.data.Connection
import com.zcq100.s3browser.data.Provider
import com.zcq100.s3browser.s3.AwsObjectStore
import com.zcq100.s3browser.s3.toUserMessage
import com.zcq100.s3browser.ui.common.Card
import com.zcq100.s3browser.ui.common.PrimaryButton
import com.zcq100.s3browser.ui.common.SecondaryButton
import com.zcq100.s3browser.ui.common.SectionLabel
import com.zcq100.s3browser.ui.common.ToggleRow
import com.zcq100.s3browser.ui.common.appViewModel
import com.zcq100.s3browser.ui.theme.Mono
import com.zcq100.s3browser.ui.theme.Palette
import kotlinx.coroutines.launch

class ConnectionEditViewModel(private val app: AppContainer, private val id: String?) : ViewModel() {
    var name by mutableStateOf("")
    var provider by mutableStateOf(Provider.MINIO)
        private set
    var endpoint by mutableStateOf("")
    var region by mutableStateOf("")
    var pathStyle by mutableStateOf(Provider.MINIO.defaultPathStyle)
    var accessKey by mutableStateOf("")
    var secretKey by mutableStateOf("")
    var testing by mutableStateOf(false)
        private set
    var testResult by mutableStateOf<Pair<Boolean, String>?>(null)
        private set
    var loaded by mutableStateOf(id == null)
        private set

    val isEdit get() = id != null

    init {
        if (id != null) viewModelScope.launch {
            app.connections.get(id)?.let {
                name = it.name; provider = it.provider; endpoint = it.endpoint; region = it.region
                pathStyle = it.pathStyle; accessKey = it.accessKeyId; secretKey = it.secretAccessKey
            }
            loaded = true
        }
    }

    fun selectProvider(p: Provider) {
        provider = p
        pathStyle = p.defaultPathStyle
    }

    val valid: Boolean
        get() = accessKey.isNotBlank() && secretKey.isNotBlank() &&
            (endpoint.isNotBlank() || provider == Provider.AWS) && (region.isNotBlank() || provider != Provider.AWS)

    private fun build() = Connection(
        id = id ?: java.util.UUID.randomUUID().toString(),
        name = name.trim().ifBlank { provider.label },
        provider = provider,
        endpoint = endpoint.trim(),
        region = region.trim(),
        pathStyle = pathStyle,
        accessKeyId = accessKey.trim(),
        secretAccessKey = secretKey.trim(),
    )

    fun test() {
        testing = true
        testResult = null
        viewModelScope.launch {
            testResult = runCatching { AwsObjectStore(build()).use { it.listBuckets() } }
                .fold({ true to "连接成功，找到 ${it.size} 个存储桶" }, { false to it.toUserMessage() })
            testing = false
        }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        app.connections.save(build())
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        id?.let { app.connections.delete(it) }
        onDone()
    }
}

@Composable
fun ConnectionEditScreen(id: String?, onClose: () -> Unit) {
    val vm = appViewModel { ConnectionEditViewModel(it, id) }
    if (!vm.loaded) return

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("取消", color = Palette.TextSecondary, fontSize = 15.sp) }
            Text(
                if (vm.isEdit) "编辑连接" else "添加连接",
                Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (vm.isEdit) {
                TextButton(onClick = { vm.delete(onClose) }) { Text("删除", color = Palette.Danger, fontSize = 15.sp) }
            } else {
                Box(Modifier.padding(horizontal = 28.dp))
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                SectionLabel("服务商")
                ProviderPicker(vm.provider, vm::selectProvider)
            }
            Field("名称", vm.name, { vm.name = it }, placeholder = vm.provider.label)
            Field(
                "Endpoint", vm.endpoint, { vm.endpoint = it }, placeholder = vm.provider.endpointExample,
                mono = true, keyboard = KeyboardType.Uri,
            )
            Field("区域", vm.region, { vm.region = it }, placeholder = vm.provider.regionExample, mono = true)
            Field("Access Key", vm.accessKey, { vm.accessKey = it }, mono = true)
            Field("Secret Key", vm.secretKey, { vm.secretKey = it }, mono = true, secret = true)

            Card {
                ToggleRow(
                    "路径风格寻址",
                    if (vm.pathStyle) "请求地址形如 endpoint/桶名/文件，适合 MinIO 等自建服务"
                    else "请求地址形如 桶名.endpoint/文件，阿里云 OSS 等只支持这种",
                    vm.pathStyle, { vm.pathStyle = it },
                )
            }

            vm.testResult?.let { (ok, msg) ->
                Text(
                    msg,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (ok) Palette.Chip else Palette.WarnBg).padding(14.dp),
                    color = if (ok) Palette.Success else Palette.WarnFg, fontSize = 13.sp, lineHeight = 19.sp,
                )
            }
            Text("Secret Key 用系统密钥库加密后只保存在这台手机上。", fontSize = 12.sp, color = Palette.Muted)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                if (vm.testing) "测试中…" else "测试连接", vm::test,
                Modifier.weight(1f), enabled = vm.valid && !vm.testing,
            )
            Box(Modifier.weight(1f)) {
                PrimaryButton("保存", { vm.save(onClose) }, enabled = vm.valid)
            }
        }
    }
}

@Composable
private fun ProviderPicker(selected: Provider, onSelect: (Provider) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Surface)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(12.dp)).clickable { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label, Modifier.weight(1f), fontSize = 15.sp)
            Icon(Icons.Outlined.ExpandMore, null, tint = Palette.Muted)
        }
        DropdownMenu(open, { open = false }) {
            Provider.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { onSelect(p); open = false })
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    mono: Boolean = false,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Column {
        SectionLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder, color = Palette.Faint, fontFamily = if (mono) Mono else null, fontSize = 14.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = if (mono) Mono else null, fontSize = 15.sp),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Palette.Surface, unfocusedContainerColor = Palette.Surface,
                unfocusedBorderColor = Palette.BorderStrong, focusedBorderColor = Palette.Accent,
            ),
        )
    }
}
