package com.zcq100.s3browser.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zcq100.s3browser.ui.theme.Palette

enum class Tab { Buckets, Shares }

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Palette.Surface) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Palette.Accent, selectedTextColor = Palette.Accent,
            unselectedIconColor = Palette.Muted, unselectedTextColor = Palette.Muted,
            indicatorColor = Palette.WarnBg,
        )
        NavigationBarItem(
            selected = current == Tab.Buckets, onClick = { onSelect(Tab.Buckets) },
            icon = { Icon(Icons.Outlined.Storage, null) }, label = { Text("存储桶") }, colors = colors,
        )
        NavigationBarItem(
            selected = current == Tab.Shares, onClick = { onSelect(Tab.Shares) },
            icon = { Icon(Icons.Outlined.Link, null) }, label = { Text("分享记录") }, colors = colors,
        )
    }
}
