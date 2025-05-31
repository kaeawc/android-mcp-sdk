package dev.jasonpearson.mcpandroidsdk.models

/** Android-specific resource models that aren't provided by the SDK */

/** Simple resource content wrapper for Android file system access */
data class AndroidResourceContent(
    val uri: String,
    val text: String?,
    val blob: ByteArray? = null,
    val mimeType: String = "text/plain"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AndroidResourceContent

        if (uri != other.uri) return false
        if (text != other.text) return false
        if (blob != null) {
            if (other.blob == null) return false
            if (!blob.contentEquals(other.blob)) return false
        } else if (other.blob != null) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (blob?.contentHashCode() ?: 0)
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
