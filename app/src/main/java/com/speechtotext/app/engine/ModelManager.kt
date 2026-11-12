package com.speechtotext.app.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.speechtotext.app.domain.Language
import com.speechtotext.app.domain.ModelDownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ModelManager(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val modelsDir: File = File(context.filesDir, "vosk-models").apply {
        if (!exists()) mkdirs()
    }

    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val _downloadStatus = MutableStateFlow<Map<String, ModelDownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, ModelDownloadStatus>> = _downloadStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    init {
        refreshStatuses()
    }

    fun refreshStatuses() {
        val currentMap = mutableMapOf<String, ModelDownloadStatus>()
        modelsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && isModelDirValid(dir)) {
                currentMap[dir.name] = ModelDownloadStatus.READY
            }
        }
        _downloadStatus.value = currentMap
    }

    fun isModelReady(modelName: String): Boolean {
        val dir = File(modelsDir, modelName)
        return dir.exists() && isModelDirValid(dir)
    }

    fun getModelDirectory(modelName: String): File? {
        val dir = File(modelsDir, modelName)
        if (dir.exists() && isModelDirValid(dir)) {
            // Find subdirectory if zipped with root folder
            val subdirs = dir.listFiles { f -> f.isDirectory }
            if (subdirs != null && subdirs.size == 1 && isModelDirValid(subdirs[0])) {
                return subdirs[0]
            }
            return dir
        }
        return null
    }

    private fun isModelDirValid(dir: File): Boolean {
        // A valid Vosk model directory contains either an 'am' or 'conf' or 'graph' folder/file,
        // or a single nested directory containing them.
        val files = dir.listFiles() ?: return false
        if (files.isEmpty()) return false
        val hasModelComponents = files.any {
            it.name.equals("am", ignoreCase = true) ||
                    it.name.equals("conf", ignoreCase = true) ||
                    it.name.equals("graph", ignoreCase = true) ||
                    it.name.equals("ivector", ignoreCase = true)
        }
        if (hasModelComponents) return true

        // Check if wrapped in a single root folder
        if (files.size == 1 && files[0].isDirectory) {
            val nestedFiles = files[0].listFiles() ?: return false
            return nestedFiles.any {
                it.name.equals("am", ignoreCase = true) ||
                        it.name.equals("conf", ignoreCase = true) ||
                        it.name.equals("graph", ignoreCase = true)
            }
        }
        return false
    }

    suspend fun downloadModel(
        language: Language,
        wifiOnly: Boolean = false,
        onStatusChange: ((ModelDownloadStatus, Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val modelName = language.modelName
        val downloadUrl = language.downloadUrl

        // Check Wi-Fi restriction if enabled
        if (wifiOnly && !isWifiConnected()) {
            return@withContext Result.failure(Exception("Wi-Fi se connect karein ya Settings me Wi-Fi only band karein"))
        }

        updateStatus(modelName, ModelDownloadStatus.DOWNLOADING, 0f)
        onStatusChange?.invoke(ModelDownloadStatus.DOWNLOADING, 0f)

        val targetDir = File(modelsDir, modelName)
        val tempZip = File(modelsDir, "$modelName.zip.tmp")

        try {
            val request = Request.Builder().url(downloadUrl).build()
            val call = okHttpClient.newCall(request)
            activeCalls[modelName] = call

            val response = call.execute()
            if (!response.isSuccessful) {
                updateStatus(modelName, ModelDownloadStatus.FAILED, 0f)
                return@withContext Result.failure(Exception("Download fail ho gaya (HTTP ${response.code})"))
            }

            val body = response.body ?: throw Exception("Empty response from server")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: language.sizeBytes

            FileOutputStream(tempZip).use { fos ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        if (totalBytes > 0) {
                            val progress = (totalDownloaded.toFloat() / totalBytes).coerceIn(0f, 0.90f)
                            updateProgress(modelName, progress)
                            onStatusChange?.invoke(ModelDownloadStatus.DOWNLOADING, progress)
                        }
                    }
                }
            }

            // Unzip model into targetDir
            updateProgress(modelName, 0.95f)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            unzipFile(tempZip, targetDir)
            tempZip.delete()

            updateStatus(modelName, ModelDownloadStatus.READY, 1.0f)
            onStatusChange?.invoke(ModelDownloadStatus.READY, 1.0f)
            Result.success(targetDir)
        } catch (e: Exception) {
            tempZip.delete()
            updateStatus(modelName, ModelDownloadStatus.FAILED, 0f)
            onStatusChange?.invoke(ModelDownloadStatus.FAILED, 0f)
            Result.failure(e)
        } finally {
            activeCalls.remove(modelName)
        }
    }

    fun cancelDownload(modelName: String) {
        activeCalls[modelName]?.cancel()
        activeCalls.remove(modelName)
        val tempZip = File(modelsDir, "$modelName.zip.tmp")
        tempZip.delete()
        updateStatus(modelName, ModelDownloadStatus.NOT_DOWNLOADED, 0f)
    }

    fun deleteModel(modelName: String): Boolean {
        val targetDir = File(modelsDir, modelName)
        val success = if (targetDir.exists()) targetDir.deleteRecursively() else true
        updateStatus(modelName, ModelDownloadStatus.NOT_DOWNLOADED, 0f)
        return success
    }

    fun deleteAllModels(): Boolean {
        val success = modelsDir.deleteRecursively()
        modelsDir.mkdirs()
        refreshStatuses()
        return success
    }

    fun getTotalStorageUsedBytes(): Long {
        return calculateDirSize(modelsDir)
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun updateStatus(modelName: String, status: ModelDownloadStatus, progress: Float) {
        val statusMap = _downloadStatus.value.toMutableMap()
        statusMap[modelName] = status
        _downloadStatus.value = statusMap

        val progressMap = _downloadProgress.value.toMutableMap()
        progressMap[modelName] = progress
        _downloadProgress.value = progressMap
    }

    private fun updateProgress(modelName: String, progress: Float) {
        val progressMap = _downloadProgress.value.toMutableMap()
        progressMap[modelName] = progress
        _downloadProgress.value = progressMap
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun unzipFile(zipFile: File, destinationDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val destCanonical = destinationDir.canonicalPath

            while (entry != null) {
                val newFile = File(destinationDir, entry.name)
                // Prevent Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(destCanonical)) {
                    throw SecurityException("Invalid zip entry: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
