package com.zcq100.s3browser.s3

import aws.sdk.kotlin.runtime.AwsServiceException
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

/** 把 SDK 异常转成用户看得懂的提示，常见的兼容性错误给出可操作的建议。 */
fun Throwable.toUserMessage(): String {
    if (this is CancellationException) return "已取消"
    val service = generateSequence(this) { it.cause }.filterIsInstance<AwsServiceException>().firstOrNull()
    if (service != null) {
        val code = service.sdkErrorMetadata.errorCode
        val hint = when (code) {
            "SignatureDoesNotMatch" -> "签名不匹配：检查 Secret Key、区域和寻址方式"
            "InvalidAccessKeyId" -> "Access Key 不存在"
            "AccessDenied" -> "没有权限执行这个操作"
            "SecondLevelDomainForbidden" -> "该服务只接受虚拟主机寻址，请在连接设置里关闭「路径风格」"
            "NoSuchBucket" -> "存储桶不存在"
            "NoSuchKey" -> "文件不存在"
            "BucketAlreadyExists", "BucketAlreadyOwnedByYou" -> "桶名已被占用"
            "InvalidBucketName" -> "桶名不符合规则"
            "NotImplemented", "MethodNotAllowed" -> "该服务不支持这个操作"
            "AuthorizationHeaderMalformed", "IllegalLocationConstraintException", "InvalidLocationConstraint" ->
                "区域不匹配：检查连接里填写的区域"
            "RequestTimeTooSkewed" -> "手机时间与服务器相差太大，请校准系统时间"
            else -> null
        }
        val detail = service.sdkErrorMetadata.errorMessage ?: service.message
        return listOfNotNull(hint, listOfNotNull(code, detail).joinToString(": ").takeIf { it.isNotBlank() })
            .joinToString("\n")
    }
    return when (this) {
        is UnknownHostException -> "找不到服务器：$message"
        is IOException -> "网络错误：${message ?: javaClass.simpleName}"
        else -> message ?: javaClass.simpleName
    }
}
