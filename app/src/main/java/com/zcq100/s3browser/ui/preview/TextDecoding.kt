package com.zcq100.s3browser.ui.preview

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class DecodedText(val text: String, val looksBinary: Boolean)

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

/**
 * 把对象开头的一段字节解码成文本。
 * 先看 BOM；没有 BOM 时按严格 UTF-8 解码，失败再退回 GB18030（中文 Windows 常见编码）。
 * 控制字符过多视为二进制文件，不展示乱码。
 */
fun decodeText(bytes: ByteArray): DecodedText {
    if (bytes.isEmpty()) return DecodedText("", false)

    val bom = when {
        bytes.startsWith(UTF8_BOM) -> Charsets.UTF_8 to 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charset.forName("UTF-16LE") to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charset.forName("UTF-16BE") to 2
        else -> null to 0
    }
    val body = if (bom.second == 0) bytes else bytes.copyOfRange(bom.second, bytes.size)
    bom.first?.let { return DecodedText(String(body, it), false) }

    val sample = body.copyOfRange(0, minOf(body.size, 8192))
    val controls = sample.count { it.toInt() in 0..8 || it.toInt() in 14..31 }
    if (sample.isNotEmpty() && controls * 100 > sample.size * 5) return DecodedText("", true)

    val text = try {
        strictDecode(body, Charsets.UTF_8)
    } catch (_: CharacterCodingException) {
        runCatching { strictDecode(body, Charset.forName("GB18030")) }.getOrNull()
    }
    // 两种编码都解不开时仍按 UTF-8 宽松解码，用替换字符标出坏字节。
    return DecodedText(text ?: String(body, Charsets.UTF_8), false)
}

/** LazyColumn 的每一行不宜太长：压缩过的 JSON 可能是一行几十万字符。 */
fun toDisplayLines(text: String, maxLineLength: Int = 2000): List<String> {
    if (text.isEmpty()) return listOf("")
    return text.split('\n').flatMap { line ->
        val clean = line.trimEnd('\r')
        if (clean.length <= maxLineLength) listOf(clean) else clean.chunked(maxLineLength)
    }
}

private fun strictDecode(bytes: ByteArray, charset: Charset): String =
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
