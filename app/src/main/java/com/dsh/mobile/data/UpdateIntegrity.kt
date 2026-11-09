package com.dsh.mobile.data

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Integrity of downloaded update packages. Downloads may go through third-party mirrors, so the APK is accepted
 * only when its SHA-256 matches GitHub's asset digest or the release's SHA256SUMS entry. Android additionally
 * refuses an upgrade signed by a different key (see ApkInstaller).
 */
object UpdateIntegrity {
    const val SUMS_FILE = "SHA256SUMS"
    private val HEX = Regex("[a-f0-9]{64}")

    /** GitHub REST `digest` field, e.g. "sha256:ab12…". */
    fun fromDigest(digest: String?): String? =
        digest?.trim()?.lowercase()?.removePrefix("sha256:")?.takeIf { HEX.matches(it) && digest.trim().lowercase().startsWith("sha256:") }

    /** `sha256sum` output: "<hex>  <name>" or "<hex> *<name>"; the entry must name exactly this asset. */
    fun fromSums(sums: String, assetName: String): String? = sums.lineSequence()
        .map { it.trim() }
        .mapNotNull { line -> Regex("^([a-fA-F0-9]{64}) [ *](.+)$").find(line)?.groupValues }
        .filter { it[2] == assetName }
        .map { it[1].lowercase() }
        .distinct()
        .singleOrNull()

    fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** Throws if the file does not match; never accepts a missing expectation. */
    fun verify(file: File, expected: String?) {
        if (expected == null || !HEX.matches(expected)) throw IOException("缺少安装包校验值，已停止更新。请到 GitHub Releases 手动下载")
        val actual = sha256(file)
        if (!MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())) {
            throw IOException("安装包校验失败（SHA-256 不一致），可能已损坏或被篡改")
        }
    }
}
