package com.zcq100.s3browser.data

/** 对象存储里可选的桶级功能。不在 [Provider.features] 里的功能，界面上直接隐藏。 */
enum class Feature { PublicReadPolicy, Versioning, ObjectLock }

/**
 * 服务商预设：只决定默认值和界面上显示哪些功能，真正的请求都走标准 S3 API。
 *
 * 功能集合是保守取值：没有确认某家通过 S3 兼容接口支持的功能，先不放进来，
 * 宁可少一个开关，也不让用户点了才报错。
 */
enum class Provider(
    val label: String,
    val endpointExample: String,
    val regionExample: String,
    /** 默认是否用路径风格（endpoint/bucket）。阿里云 OSS 只接受虚拟主机风格。 */
    val defaultPathStyle: Boolean,
    val features: Set<Feature>,
    /** 创建桶时是否发送 LocationConstraint。只有 AWS 需要，其他厂商的桶区域由 endpoint 决定。 */
    val sendLocationConstraint: Boolean = false,
) {
    AWS(
        label = "Amazon S3",
        endpointExample = "留空则按区域自动生成",
        regionExample = "us-east-1",
        defaultPathStyle = false,
        features = Feature.entries.toSet(),
        sendLocationConstraint = true,
    ),
    MINIO(
        label = "MinIO / 自建",
        endpointExample = "http://192.168.1.10:9000",
        regionExample = "us-east-1",
        defaultPathStyle = true,
        features = Feature.entries.toSet(),
    ),
    ALIYUN_OSS(
        label = "阿里云 OSS",
        endpointExample = "https://oss-cn-hangzhou.aliyuncs.com",
        regionExample = "oss-cn-hangzhou",
        defaultPathStyle = false,
        features = setOf(Feature.Versioning),
    ),
    TENCENT_COS(
        label = "腾讯云 COS",
        endpointExample = "https://cos.ap-guangzhou.myqcloud.com",
        regionExample = "ap-guangzhou",
        defaultPathStyle = false,
        features = setOf(Feature.Versioning),
    ),
    HUAWEI_OBS(
        label = "华为云 OBS",
        endpointExample = "https://obs.cn-north-4.myhuaweicloud.com",
        regionExample = "cn-north-4",
        defaultPathStyle = false,
        features = setOf(Feature.Versioning),
    ),
    QINIU_KODO(
        label = "七牛云 Kodo",
        endpointExample = "https://s3.cn-east-1.qiniucs.com",
        regionExample = "cn-east-1",
        defaultPathStyle = false,
        features = emptySet(),
    ),
    CLOUDFLARE_R2(
        label = "Cloudflare R2",
        endpointExample = "https://<账户ID>.r2.cloudflarestorage.com",
        regionExample = "auto",
        defaultPathStyle = true,
        features = emptySet(),
    ),
    OTHER(
        label = "其他 S3 兼容",
        endpointExample = "https://s3.example.com",
        regionExample = "us-east-1",
        defaultPathStyle = true,
        features = emptySet(),
    ),
}
