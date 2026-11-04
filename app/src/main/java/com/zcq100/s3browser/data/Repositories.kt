package com.zcq100.s3browser.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore("s3browser")

private val json = Json { ignoreUnknownKeys = true }

/** 一个对象存储连接。Secret Key 只在内存里是明文。 */
data class Connection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val provider: Provider,
    val endpoint: String,
    val region: String,
    val pathStyle: Boolean,
    val accessKeyId: String,
    val secretAccessKey: String,
) {
    val host: String
        get() = endpoint.takeIf { it.isNotBlank() }
            ?.let { runCatching { URI(it).let { u -> if (u.port > 0) "${u.host}:${u.port}" else u.host } }.getOrNull() }
            ?: "s3.$region.amazonaws.com"

    val maskedAccessKey: String
        get() = if (accessKeyId.length <= 8) accessKeyId else accessKeyId.take(4) + "····" + accessKeyId.takeLast(4)
}

@Serializable
private data class StoredConnection(
    val id: String,
    val name: String,
    val provider: Provider,
    val endpoint: String,
    val region: String,
    val pathStyle: Boolean,
    val accessKeyId: String,
    val secretEnc: String,
)

class ConnectionRepository(private val store: DataStore<Preferences>) {
    private val listKey = stringPreferencesKey("connections")
    private val activeKey = stringPreferencesKey("active_connection")

    val connections: Flow<List<Connection>> = store.data.map { prefs -> decode(prefs[listKey]) }

    val active: Flow<Connection?> = combine(connections, store.data.map { it[activeKey] }) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    suspend fun get(id: String): Connection? = connections.first().firstOrNull { it.id == id }

    suspend fun save(conn: Connection) {
        store.edit { prefs ->
            val list = decode(prefs[listKey]).filterNot { it.id == conn.id } + conn
            prefs[listKey] = encode(list)
            prefs[activeKey] = conn.id
        }
    }

    suspend fun delete(id: String) {
        store.edit { prefs ->
            prefs[listKey] = encode(decode(prefs[listKey]).filterNot { it.id == id })
            if (prefs[activeKey] == id) prefs.remove(activeKey)
        }
    }

    suspend fun setActive(id: String) {
        store.edit { it[activeKey] = id }
    }

    private fun decode(raw: String?): List<Connection> {
        if (raw.isNullOrBlank()) return emptyList()
        return json.decodeFromString<List<StoredConnection>>(raw).mapNotNull { s ->
            val secret = runCatching { SecretCipher.decrypt(s.secretEnc) }.getOrNull() ?: return@mapNotNull null
            Connection(s.id, s.name, s.provider, s.endpoint, s.region, s.pathStyle, s.accessKeyId, secret)
        }
    }

    private fun encode(list: List<Connection>): String = json.encodeToString(
        list.map {
            StoredConnection(
                it.id, it.name, it.provider, it.endpoint, it.region, it.pathStyle,
                it.accessKeyId, SecretCipher.encrypt(it.secretAccessKey),
            )
        },
    )
}

/** 本机分享记录。只是签发过的预签名链接的备忘，删除记录不会让链接失效。 */
@Serializable
data class ShareRecord(
    val id: String = UUID.randomUUID().toString(),
    val connectionId: String,
    val connectionName: String,
    val bucket: String,
    val key: String,
    val url: String,
    val createdAt: Long,
    val expiresAt: Long,
    val attachment: Boolean,
) {
    val fileName: String get() = key.substringAfterLast('/')
}

class ShareRepository(private val store: DataStore<Preferences>) {
    private val listKey = stringPreferencesKey("shares")

    val records: Flow<List<ShareRecord>> = store.data.map { prefs ->
        prefs[listKey]?.let { json.decodeFromString<List<ShareRecord>>(it) }.orEmpty()
            .sortedByDescending { it.createdAt }
    }

    suspend fun add(record: ShareRecord) = update { it + record }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    private suspend fun update(block: (List<ShareRecord>) -> List<ShareRecord>) {
        store.edit { prefs ->
            val list = prefs[listKey]?.let { json.decodeFromString<List<ShareRecord>>(it) }.orEmpty()
            prefs[listKey] = json.encodeToString(block(list))
        }
    }
}
