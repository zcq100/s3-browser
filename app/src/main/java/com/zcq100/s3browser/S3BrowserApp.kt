package com.zcq100.s3browser

import android.app.Application
import android.content.Context
import com.zcq100.s3browser.data.Connection
import com.zcq100.s3browser.data.ConnectionRepository
import com.zcq100.s3browser.data.ShareRepository
import com.zcq100.s3browser.data.appDataStore
import com.zcq100.s3browser.s3.AwsObjectStore
import com.zcq100.s3browser.s3.ObjectStore
import kotlinx.coroutines.flow.first

class S3BrowserApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(val context: Context) {
    val connections = ConnectionRepository(context.appDataStore)
    val shares = ShareRepository(context.appDataStore)

    private var cached: Pair<Connection, ObjectStore>? = null

    /** 当前连接和它的客户端；连接配置变了就重建客户端。 */
    @Synchronized
    fun storeFor(conn: Connection): ObjectStore {
        cached?.let { (c, s) -> if (c == conn) return s else s.close() }
        return AwsObjectStore(conn).also { cached = conn to it }
    }

    suspend fun active(): Pair<Connection, ObjectStore> {
        val conn = connections.active.first() ?: error("还没有添加连接")
        return conn to storeFor(conn)
    }
}
