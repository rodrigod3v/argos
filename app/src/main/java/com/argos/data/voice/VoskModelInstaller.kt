package com.argos.data.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * O modelo Vosk mora no APK (assets) e e extraido para filesDir no primeiro uso.
 *
 * Vosk precisa de caminho real de arquivo — assets nao serve. A extracao e unica:
 * ~54 MB, uma vez, sem rede.
 */
class VoskModelInstaller(private val context: Context) {

    /** Extrai o modelo se ainda nao estiver no disco. Bloqueante — chamar fora da main. */
    fun ensure(): File {
        val target = File(context.filesDir, MODEL_DIR)
        if (File(target, MARKER).exists()) return target

        val staging = File(context.filesDir, STAGING_DIR)
        staging.deleteRecursively()
        staging.mkdirs()

        val startedAt = System.currentTimeMillis()
        Log.i(TAG, "extraindo modelo de assets...")

        context.assets.open(ASSET_ZIP).use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    val out = File(staging, name)
                    // protecao contra zip-slip
                    if (!out.canonicalPath.startsWith(staging.canonicalPath + File.separator) &&
                        out.canonicalPath != staging.canonicalPath
                    ) {
                        zip.closeEntry()
                        entry = zip.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output -> zip.copyTo(output, COPY_BUFFER) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val extracted = File(staging, MODEL_DIR)
        check(File(extracted, MARKER).exists()) { "modelo invalido em assets" }

        target.deleteRecursively()
        if (!extracted.renameTo(target)) {
            extracted.copyRecursively(target, overwrite = true)
            extracted.deleteRecursively()
        }
        staging.deleteRecursively()

        Log.i(TAG, "modelo instalado em ${System.currentTimeMillis() - startedAt} ms")
        return target
    }

    companion object {
        private const val TAG = "Argos"
        const val MODEL_DIR = "vosk-model-small-pt-0.3"
        private const val STAGING_DIR = "vosk-staging"
        private const val ASSET_ZIP = "vosk-model-small-pt-0.3.zip"
        private const val MARKER = "final.mdl"
        private const val COPY_BUFFER = 64 * 1024

        /** true se o APK realmente carrega o modelo. */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.open(ASSET_ZIP).use { true }
        }.getOrDefault(false)
    }
}
