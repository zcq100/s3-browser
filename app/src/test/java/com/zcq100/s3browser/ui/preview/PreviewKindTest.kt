package com.zcq100.s3browser.ui.preview

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewKindTest {

    @Test
    fun `文本类型进入内置查看`() {
        assertEquals(PreviewKind.TEXT, PreviewKind.of("readme.md"))
        assertEquals(PreviewKind.TEXT, PreviewKind.of("config.JSON"))
        assertEquals(PreviewKind.TEXT, PreviewKind.of("Main.kt"))
    }

    @Test
    fun `图片类型进入内置查看`() {
        assertEquals(PreviewKind.IMAGE, PreviewKind.of("photo.JPG"))
        assertEquals(PreviewKind.IMAGE, PreviewKind.of("shot.webp"))
    }

    @Test
    fun `视频在应用内播放`() {
        assertEquals(PreviewKind.VIDEO, PreviewKind.of("clip.mp4"))
        assertEquals(PreviewKind.VIDEO, PreviewKind.of("movie.MKV"))
        assertEquals(PreviewKind.VIDEO, PreviewKind.of("a.webm"))
    }

    @Test
    fun `音频和 SVG 交给其他应用`() {
        assertEquals(PreviewKind.MEDIA, PreviewKind.of("song.flac"))
        assertEquals(PreviewKind.MEDIA, PreviewKind.of("voice.mp3"))
        assertEquals(PreviewKind.MEDIA, PreviewKind.of("drawing.svg"))
    }

    @Test
    fun `不认识的后缀不提供查看`() {
        assertNull(PreviewKind.of("archive.zip"))
        assertNull(PreviewKind.of("app.apk"))
        assertNull(PreviewKind.of("README"))
    }

    @Test
    fun `外部打开使用具体 MIME 类型`() {
        assertEquals("video/mp4", mediaMimeType("a.mp4"))
        assertEquals("video/x-matroska", mediaMimeType("a.mkv"))
        assertEquals("audio/mpeg", mediaMimeType("a.mp3"))
        assertEquals("*/*", mediaMimeType("a.bin"))
    }

    @Test
    fun `UTF-8 文本正常解码`() {
        val decoded = decodeText("你好，S3\n第二行".toByteArray())
        assertFalse(decoded.looksBinary)
        assertEquals("你好，S3\n第二行", decoded.text)
    }

    @Test
    fun `UTF-8 BOM 会被去掉`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "plain".toByteArray()
        assertEquals("plain", decodeText(bytes).text)
    }

    @Test
    fun `UTF-16LE BOM 文本不被当成二进制`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "文本".toByteArray(Charset.forName("UTF-16LE"))
        val decoded = decodeText(bytes)
        assertFalse(decoded.looksBinary)
        assertEquals("文本", decoded.text)
    }

    @Test
    fun `非 UTF-8 的中文编码回退到 GB18030`() {
        val bytes = "中文内容".toByteArray(Charset.forName("GB18030"))
        assertEquals("中文内容", decodeText(bytes).text)
    }

    @Test
    fun `含大量控制字符的字节视为二进制`() {
        val bytes = ByteArray(200) { 0 }
        assertTrue(decodeText(bytes).looksBinary)
    }

    @Test
    fun `超长行按固定长度切块`() {
        val lines = toDisplayLines("abc\n" + "x".repeat(4500), maxLineLength = 2000)
        assertEquals(listOf("abc", "x".repeat(2000), "x".repeat(2000), "x".repeat(500)), lines)
    }

    @Test
    fun `空文本也有一行可显示`() {
        assertEquals(listOf(""), toDisplayLines(""))
    }
}
