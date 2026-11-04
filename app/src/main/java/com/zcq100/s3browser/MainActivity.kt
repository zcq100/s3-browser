package com.zcq100.s3browser

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zcq100.s3browser.ui.buckets.BucketsScreen
import com.zcq100.s3browser.ui.buckets.NewBucketScreen
import com.zcq100.s3browser.ui.common.Tab
import com.zcq100.s3browser.ui.connection.ConnectionEditScreen
import com.zcq100.s3browser.ui.files.FilesScreen
import com.zcq100.s3browser.ui.share.ShareScreen
import com.zcq100.s3browser.ui.share.SharesScreen
import com.zcq100.s3browser.ui.theme.S3BrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            S3BrowserTheme { AppNav() }
        }
    }
}

private object Routes {
    const val BUCKETS = "buckets"
    const val SHARES = "shares"
    const val NEW_BUCKET = "bucket/new"
    const val CONNECTION = "connection?id={id}"
    const val FILES = "files/{bucket}"
    const val SHARE = "share/{bucket}?key={key}"

    fun connection(id: String?) = if (id == null) "connection" else "connection?id=${Uri.encode(id)}"
    fun files(bucket: String) = "files/${Uri.encode(bucket)}"
    fun share(bucket: String, key: String) = "share/${Uri.encode(bucket)}?key=${Uri.encode(key)}"
}

@androidx.compose.runtime.Composable
private fun AppNav() {
    val nav = rememberNavController()
    val onTab: (Tab) -> Unit = { tab ->
        val route = if (tab == Tab.Buckets) Routes.BUCKETS else Routes.SHARES
        nav.navigate(route) {
            popUpTo(Routes.BUCKETS) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(nav, startDestination = Routes.BUCKETS) {
        composable(Routes.BUCKETS) {
            BucketsScreen(
                onOpenBucket = { nav.navigate(Routes.files(it)) },
                onNewBucket = { nav.navigate(Routes.NEW_BUCKET) },
                onEditConnection = { nav.navigate(Routes.connection(it)) },
                onTab = onTab,
            )
        }
        composable(Routes.SHARES) {
            SharesScreen(onTab = onTab, onRegenerate = { b, k -> nav.navigate(Routes.share(b, k)) })
        }
        composable(Routes.NEW_BUCKET) {
            NewBucketScreen(onClose = { nav.popBackStack() }, onCreated = { nav.popBackStack() })
        }
        composable(
            Routes.CONNECTION,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            ConnectionEditScreen(entry.arguments?.getString("id"), onClose = { nav.popBackStack() })
        }
        composable(Routes.FILES, arguments = listOf(navArgument("bucket") { type = NavType.StringType })) { entry ->
            FilesScreen(
                bucket = entry.arguments!!.getString("bucket")!!,
                onBack = { nav.popBackStack() },
                onShare = { b, k -> nav.navigate(Routes.share(b, k)) },
            )
        }
        composable(
            Routes.SHARE,
            arguments = listOf(
                navArgument("bucket") { type = NavType.StringType },
                navArgument("key") { type = NavType.StringType },
            ),
        ) { entry ->
            ShareScreen(
                bucket = entry.arguments!!.getString("bucket")!!,
                key = entry.arguments!!.getString("key")!!,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
                // 先关掉已完成的分享页，否则切回「存储桶」标签时会恢复到它。
                onHistory = { nav.popBackStack(); nav.toShares() },
            )
        }
    }
}

private fun NavHostController.toShares() = navigate(Routes.SHARES) {
    popUpTo(Routes.BUCKETS) { saveState = true }
    launchSingleTop = true
}
