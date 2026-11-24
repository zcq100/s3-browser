package com.zcq100.s3browser.ui.preview

/**
 * 文件的查看方式：
 * - 文本、图片、视频在应用内查看（视频用预签名链接直接流式播放）；
 * - 音频、SVG 等交给系统里已安装的其他应用处理。
 */
enum class PreviewKind(val label: String) {
    TEXT("查看文本"),
    IMAGE("查看图片"),
    VIDEO("播放视频"),
    MEDIA("用其他应用打开");

    companion object {
        fun of(name: String): PreviewKind? = when (name.substringAfterLast('.', "").lowercase()) {
            in TEXT_EXTENSIONS -> TEXT
            in IMAGE_EXTENSIONS -> IMAGE
            in VIDEO_EXTENSIONS -> VIDEO
            in MEDIA_EXTENSIONS -> MEDIA
            else -> null
        }
    }
}

/** 交给外部应用时使用的 MIME 类型；认不出来就用通配，让系统自己挑。 */
fun mediaMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "mov" -> "video/quicktime"
    "mkv" -> "video/x-matroska"
    "avi" -> "video/x-msvideo"
    "webm" -> "video/webm"
    "3gp" -> "video/3gpp"
    "wmv" -> "video/x-ms-wmv"
    "flv" -> "video/x-flv"
    "svg" -> "image/svg+xml"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "flac" -> "audio/flac"
    "wav" -> "audio/x-wav"
    "aac" -> "audio/aac"
    "ogg", "opus" -> "audio/ogg"
    else -> "*/*"
}

// SVG 能显示但不能用 BitmapFactory 解码，所以归到外部应用。
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "csv", "tsv", "json", "log", "xml",
    "yaml", "yml", "ini", "conf", "properties", "toml",
    "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "css", "html", "htm",
    "sh", "bash", "zsh", "fish", "sql", "gradle", "srt", "vtt", "diff", "patch",
)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")

private val MEDIA_EXTENSIONS = setOf(
    "mp3", "m4a", "flac", "wav", "aac", "ogg", "opus", "svg",
)

private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "mkv", "avi", "webm", "3gp", "wmv", "flv")
