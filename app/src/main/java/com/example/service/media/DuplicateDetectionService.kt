package com.amresalehin.emreshots.service.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.amresalehin.emreshots.data.model.ScreenshotItem
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs

data class DuplicateGroup(
    val kind: Kind,
    val items: List<ScreenshotItem>
) {
    enum class Kind { EXACT, VISUAL }
}

class DuplicateDetectionService(private val context: Context) {
    suspend fun findDuplicates(items: List<ScreenshotItem>): List<DuplicateGroup> {
        val photos = items.filter { !it.isVideo }
        val exactGroups = mutableListOf<DuplicateGroup>()
        val byHash = linkedMapOf<String, MutableList<ScreenshotItem>>()

        for (item in photos) {
            val hash = contentHash(item) ?: continue
            byHash.getOrPut(hash) { mutableListOf() }.add(item)
        }
        byHash.values.filter { it.size > 1 }.forEach {
            exactGroups += DuplicateGroup(DuplicateGroup.Kind.EXACT, it.toList())
        }

        val exactIds = exactGroups.flatMap { it.items }.map { it.id }.toSet()
        val candidates = photos.filterNot { exactIds.contains(it.id) }
        val hashes = candidates.mapNotNull { item ->
            averageHash(item)?.let { item to it }
        }.toMutableList()

        val consumed = mutableSetOf<String>()
        for (i in hashes.indices) {
            val first = hashes[i].first
            if (consumed.contains(first.id)) continue
            val group = mutableListOf(first)
            for (j in i + 1 until hashes.size) {
                val other = hashes[j].first
                if (consumed.contains(other.id)) continue
                if (hammingDistance(hashes[i].second, hashes[j].second) <= 6) {
                    group += other
                }
            }
            if (group.size > 1) {
                group.forEach { consumed += it.id }
                exactGroups += DuplicateGroup(DuplicateGroup.Kind.VISUAL, group)
            }
        }
        return exactGroups
    }

    private fun openStream(item: ScreenshotItem): InputStream? {
        val direct = File(item.filePath)
        if (direct.exists() && direct.isFile) return direct.inputStream()
        val uri = item.uriString?.let(Uri::parse) ?: return null
        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    private fun contentHash(item: ScreenshotItem): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        openStream(item)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun averageHash(item: ScreenshotItem): Long? = runCatching {
        val bitmap = openStream(item)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var total = 0L
        val pixels = IntArray(64)
        scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        for (pixel in pixels) {
            total += ((pixel shr 16 and 0xff) * 299L +
                    (pixel shr 8 and 0xff) * 587L +
                    (pixel and 0xff) * 114L) / 1000L
        }
        val average = total / 64L
        var hash = 0L
        pixels.forEachIndexed { index, pixel ->
            val gray = ((pixel shr 16 and 0xff) * 299L +
                    (pixel shr 8 and 0xff) * 587L +
                    (pixel and 0xff) * 114L) / 1000L
            if (gray >= average) hash = hash or (1L shl index)
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        hash
    }.getOrNull()

    private fun hammingDistance(a: Long, b: Long): Int =
        java.lang.Long.bitCount(a xor b)
}
