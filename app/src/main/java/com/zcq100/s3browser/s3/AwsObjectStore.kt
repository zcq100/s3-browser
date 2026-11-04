package com.zcq100.s3browser.s3

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.copyObject
import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.getBucketPolicy
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.BucketLocationConstraint
import aws.sdk.kotlin.services.s3.model.BucketVersioningStatus
import aws.sdk.kotlin.services.s3.model.CreateBucketConfiguration
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListBucketsRequest
import aws.sdk.kotlin.services.s3.model.VersioningConfiguration
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.putBucketPolicy
import aws.sdk.kotlin.services.s3.putBucketVersioning
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.client.config.RequestHttpChecksumConfig
import aws.smithy.kotlin.runtime.client.config.ResponseHttpChecksumConfig
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.net.url.Url
import com.zcq100.s3browser.data.Connection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.InputStream
import java.net.URLEncoder
import kotlin.time.Duration

/**
 * 基于 AWS SDK for Kotlin 的实现，配置偏向兼容第三方 S3 服务：
 * - 只在 API 要求时才计算/校验校验和，不默认附加 CRC32（很多兼容服务不认）；
 * - 关闭 aws-chunked 流式上传，用普通的 Content-Length + 完整签名上传；
 * - 删除逐个调用 DeleteObject，不用 DeleteObjects（它强制要求校验和头，各家要求不一）。
 */
class AwsObjectStore(private val conn: Connection) : ObjectStore {

    private val client = S3Client {
        region = conn.region.ifBlank { "us-east-1" }
        if (conn.endpoint.isNotBlank()) endpointUrl = Url.parse(conn.endpoint.trim().trimEnd('/'))
        forcePathStyle = conn.pathStyle
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = conn.accessKeyId
            secretAccessKey = conn.secretAccessKey
        }
        requestChecksumCalculation = RequestHttpChecksumConfig.WHEN_REQUIRED
        responseChecksumValidation = ResponseHttpChecksumConfig.WHEN_REQUIRED
        enableAwsChunked = false
    }

    override suspend fun listBuckets(): List<BucketInfo> =
        client.listBuckets(ListBucketsRequest {}).buckets.orEmpty()
            .mapNotNull { b -> b.name?.let { BucketInfo(it, b.creationDate?.toEpochMillis()) } }
            .sortedBy { it.name }

    override suspend fun isPublicRead(bucket: String): Boolean {
        val policy = runCatching { client.getBucketPolicy { this.bucket = bucket }.policy }.getOrNull() ?: return false
        return policy.contains("s3:GetObject") && PUBLIC_PRINCIPAL.containsMatchIn(policy) && policy.contains("\"Allow\"")
    }

    override suspend fun list(bucket: String, prefix: String, continuationToken: String?): ListPage {
        val r = client.listObjectsV2 {
            this.bucket = bucket
            this.prefix = prefix.ifEmpty { null }
            delimiter = "/"
            this.continuationToken = continuationToken
            maxKeys = 500
        }
        val folders = r.commonPrefixes.orEmpty().mapNotNull { it.prefix }.map { ObjectEntry(it, isFolder = true) }
        val files = r.contents.orEmpty()
            .filter { it.key != null && it.key != prefix }
            .map { ObjectEntry(it.key!!, isFolder = false, size = it.size ?: 0, lastModified = it.lastModified?.toEpochMillis()) }
        return ListPage(folders + files, if (r.isTruncated == true) r.nextContinuationToken else null)
    }

    override suspend fun head(bucket: String, key: String): ObjectDetail {
        val r = client.headObject { this.bucket = bucket; this.key = key }
        return ObjectDetail(
            key = key,
            size = r.contentLength ?: 0,
            contentType = r.contentType,
            eTag = r.eTag?.trim('"'),
            lastModified = r.lastModified?.toEpochMillis(),
            storageClass = r.storageClass?.value ?: "STANDARD",
        )
    }

    /**
     * 关掉 aws-chunked 之后，SigV4 要先算整个请求体的 SHA-256 再发送，所以请求体必须能读两遍。
     * 这里每次读取都重新打开源文件，不需要先复制到临时文件。
     */
    override suspend fun put(bucket: String, key: String, length: Long, contentType: String?, open: () -> InputStream) {
        client.putObject {
            this.bucket = bucket
            this.key = key
            contentLength = length
            this.contentType = contentType
            body = object : ByteStream.SourceStream() {
                override val contentLength: Long = length
                override val isOneShot: Boolean = false
                override fun readFrom(): SdkSource = open().source()
            }
        }
    }

    override suspend fun createFolder(bucket: String, prefix: String) {
        client.putObject {
            this.bucket = bucket
            key = if (prefix.endsWith('/')) prefix else "$prefix/"
            contentLength = 0
            body = ByteStream.fromBytes(ByteArray(0))
        }
    }

    override suspend fun delete(bucket: String, keys: List<String>) = coroutineScope {
        val limit = Semaphore(4)
        keys.map { k -> async { limit.withPermit { client.deleteObject { this.bucket = bucket; key = k } } } }.awaitAll()
        Unit
    }

    override suspend fun rename(bucket: String, fromKey: String, toKey: String) {
        client.copyObject {
            this.bucket = bucket
            key = toKey
            copySource = "$bucket/${encodePath(fromKey)}"
        }
        client.deleteObject { this.bucket = bucket; key = fromKey }
    }

    override suspend fun createBucket(options: NewBucketOptions) {
        val name = options.name
        client.createBucket {
            bucket = name
            if (conn.provider.sendLocationConstraint && conn.region.isNotBlank() && conn.region != "us-east-1") {
                createBucketConfiguration = CreateBucketConfiguration {
                    locationConstraint = BucketLocationConstraint.fromValue(conn.region)
                }
            }
            if (options.objectLock) objectLockEnabledForBucket = true
        }
        // 开启对象锁定时服务端会自动打开版本控制。
        if (options.versioning && !options.objectLock) {
            client.putBucketVersioning {
                bucket = name
                versioningConfiguration = VersioningConfiguration { status = BucketVersioningStatus.Enabled }
            }
        }
        if (options.publicRead) {
            client.putBucketPolicy {
                bucket = name
                policy = """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":["s3:GetObject"],"Resource":["arn:aws:s3:::$name/*"]}]}"""
            }
        }
    }

    override suspend fun presignGet(bucket: String, key: String, ttl: Duration, attachment: Boolean): String {
        val request = GetObjectRequest {
            this.bucket = bucket
            this.key = key
            if (attachment) {
                val fileName = key.substringAfterLast('/')
                responseContentDisposition = "attachment; filename*=UTF-8''${encodeComponent(fileName)}"
            }
        }
        return client.presignGetObject(request, minOf(ttl, ObjectStore.MAX_PRESIGN_TTL)).url.toString()
    }

    override fun close() = client.close()

    private companion object {
        val PUBLIC_PRINCIPAL = Regex("\"Principal\"\\s*:\\s*(\"\\*\"|\\{\\s*\"AWS\"\\s*:\\s*(\\[\\s*)?\"\\*\")")

        fun encodeComponent(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        fun encodePath(key: String): String = key.split('/').joinToString("/") { encodeComponent(it) }
    }
}

private fun aws.smithy.kotlin.runtime.time.Instant.toEpochMillis(): Long =
    epochSeconds * 1000 + nanosecondsOfSecond / 1_000_000
