package com.kooduXA.opendash.domain.model

import kotlin.math.log10
import kotlin.math.pow

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long
) {
    val usedBytes: Long
        get() = totalBytes - freeBytes

    val percentageUsed: Float
        get() = if (totalBytes > 0) {
            (usedBytes.toFloat() / totalBytes.toFloat()) * 100f
        } else {
            0f
        }

    val formattedTotalSpace: String get() = formatBytes(totalBytes)
    val formattedFreeSpace: String get() = formatBytes(freeBytes)
    val formattedUsedSpace: String get() = formatBytes(usedBytes)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format("%.2f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}