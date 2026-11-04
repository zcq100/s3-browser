package com.zcq100.s3browser.s3

import java.io.Closeable
import java.io.InputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class BucketInfo(val name: String, val createdAt: Long?)

data class ObjectEntry(
    val key: String,
    val isFolder: Boolean,
    val size: Long = 0,
    val lastModified: Long? = null,
) {
    val name: String get() = key.trimEnd('/').substringAfterLast('/')
}

data class ListPage(val entries: List<ObjectEntry>, val nextToken: String?)

data class ObjectDetail(
    val key: String,
    val size: Long,
    val contentType: String?,
    val eTag: String?,
    val lastModified: Long?,
    val storageClass: String?,
)

data class NewBucketOptions(
    val name: String,
    val publicRead: Boolean,
    val versioning: Boolean,
    val objectLock: Boolean,
)

/** 应用只通过这个接口访问对象存储，换 SDK 时只改实现。 */
interface ObjectStore : Closeable {
    suspend fun listBuckets(): List<BucketInfo>
    suspend fun isPublicRead(bucket: String): Boolean
    suspend fun list(bucket: String, prefix: String, continuationToken: String?): ListPage
    suspend fun head(bucket: String, key: String): ObjectDetail
    suspend fun put(bucket: String, key: String, length: Long, contentType: String?, open: () -> InputStream)
    suspend fun createFolder(bucket: String, prefix: String)
    suspend fun delete(bucket: String, keys: List<String>)
    suspend fun rename(bucket: String, fromKey: String, toKey: String)
    suspend fun createBucket(options: NewBucketOptions)
    suspend fun presignGet(bucket: String, key: String, ttl: Duration, attachment: Boolean): String

    companion object {
        /** SigV4 预签名的上限。 */
        val MAX_PRESIGN_TTL: Duration = 7.days
    }
}
