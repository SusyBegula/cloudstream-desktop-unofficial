package com.lagradost.cloudstream3.desktop.download

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.DownloadedItemRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus {
    Queued,
    Downloading,
    Completed,
    Failed,
    Cancelled,
}

data class ActiveDownload(
    val id: String,
    val showName: String,
    val showUrl: String,
    val episodeId: String?,
    val episodeTitle: String,
    val season: Int?,
    val episode: Int?,
    val posterUrl: String?,
    val streamUrl: String,
    val localFilePath: String,
    var status: DownloadStatus = DownloadStatus.Queued,
    var progress: Float = 0f,
    var speed: String = "",
    var totalBytes: Long = 0L,
    var error: String? = null,
    @Transient internal var process: Process? = null,
    @Transient internal var job: Job? = null,
)

object FfmpegDownloadManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = ConcurrentHashMap<String, ActiveDownload>()

    private val _downloadsFlow = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val downloadsFlow: StateFlow<List<ActiveDownload>> = _downloadsFlow.asStateFlow()

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    fun isEpisodeDownloaded(showUrl: String, episodeId: String?): Boolean {
        return DesktopDataStore.getDownloadedEpisode(showUrl, episodeId) != null
    }

    fun getDownloadedRecord(showUrl: String, episodeId: String?): DownloadedItemRecord? {
        return DesktopDataStore.getDownloadedEpisode(showUrl, episodeId)
    }

    fun getActiveDownload(showUrl: String, episodeId: String?): ActiveDownload? {
        val targetId = "${showUrl}_$episodeId"
        return activeDownloads[targetId]
    }

    fun startDownload(
        showName: String,
        showUrl: String,
        episodeId: String?,
        episodeTitle: String,
        season: Int?,
        episode: Int?,
        posterUrl: String?,
        link: ExtractorLink,
    ): ActiveDownload {
        val downloadId = "${showUrl}_$episodeId"

        activeDownloads[downloadId]?.let { existing ->
            if (existing.status == DownloadStatus.Downloading || existing.status == DownloadStatus.Queued) {
                return existing
            }
        }

        val showFolder = File(PlatformPaths.downloadsDir, sanitizeFileName(showName))
        val targetDir = if (season != null) {
            File(showFolder, "Season $season")
        } else {
            showFolder
        }
        targetDir.mkdirs()

        val fileName = buildString {
            if (season != null && episode != null) {
                append("S%02dE%02d - ".format(season, episode))
            } else if (episode != null) {
                append("E%02d - ".format(episode))
            }
            append(sanitizeFileName(episodeTitle))
            append(".mp4")
        }

        val destFile = File(targetDir, fileName)

        val download = ActiveDownload(
            id = downloadId,
            showName = showName,
            showUrl = showUrl,
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            season = season,
            episode = episode,
            posterUrl = posterUrl,
            streamUrl = link.url,
            localFilePath = destFile.absolutePath,
            status = DownloadStatus.Queued,
        )

        activeDownloads[downloadId] = download
        notifyUpdate()

        download.job = scope.launch {
            runFfmpeg(download, link, destFile)
        }

        return download
    }

    private suspend fun runFfmpeg(download: ActiveDownload, link: ExtractorLink, destFile: File) {
        withContext(Dispatchers.IO) {
            try {
                download.status = DownloadStatus.Downloading
                notifyUpdate()

                val headerBuilder = StringBuilder()
                link.headers.forEach { (k, v) ->
                    headerBuilder.append("$k: $v\r\n")
                }
                if (link.referer.isNotBlank() && !link.headers.keys.any { it.equals("referer", ignoreCase = true) }) {
                    headerBuilder.append("Referer: ${link.referer}\r\n")
                }

                val cmd = mutableListOf(
                    "ffmpeg",
                    "-y",
                )

                val headerStr = headerBuilder.toString()
                if (headerStr.isNotBlank()) {
                    cmd.add("-headers")
                    cmd.add(headerStr)
                }

                cmd.add("-i")
                cmd.add(link.url)
                cmd.add("-c")
                cmd.add("copy")
                cmd.add("-bsf:a")
                cmd.add("aac_adtstoasc")
                cmd.add("-progress")
                cmd.add("pipe:1")
                cmd.add(destFile.absolutePath)

                AppLogger.d("Starting FFmpeg download: ${cmd.joinToString(" ")}")

                val pb = ProcessBuilder(cmd)
                pb.redirectErrorStream(true)
                val process = pb.start()
                download.process = process

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                var lastSize = 0L

                while (reader.readLine().also { line = it } != null) {
                    if (!isActive) {
                        process.destroyForcibly()
                        break
                    }
                    val l = line?.trim() ?: continue

                    if (l.startsWith("total_size=")) {
                        val bytes = l.substringAfter("total_size=").toLongOrNull() ?: 0L
                        if (bytes > 0) {
                            download.totalBytes = bytes
                            lastSize = bytes
                        }
                    } else if (l.startsWith("speed=")) {
                        download.speed = l.substringAfter("speed=").trim()
                    } else if (l.startsWith("progress=")) {
                        val progState = l.substringAfter("progress=").trim()
                        if (progState == "end") {
                            download.progress = 1.0f
                        }
                        notifyUpdate()
                    }
                }

                val exitCode = process.waitFor()
                if (exitCode == 0 && destFile.exists() && destFile.length() > 0) {
                    download.status = DownloadStatus.Completed
                    download.progress = 1.0f
                    download.totalBytes = destFile.length()

                    DesktopDataStore.saveDownload(
                        DownloadedItemRecord(
                            id = download.id,
                            showName = download.showName,
                            showUrl = download.showUrl,
                            episodeId = download.episodeId,
                            episodeTitle = download.episodeTitle,
                            season = download.season,
                            episode = download.episode,
                            posterUrl = download.posterUrl,
                            localFilePath = destFile.absolutePath,
                            totalBytes = destFile.length(),
                        ),
                    )
                    AppLogger.d("FFmpeg download completed: ${destFile.absolutePath}")
                } else if (download.status != DownloadStatus.Cancelled) {
                    download.status = DownloadStatus.Failed
                    download.error = "FFmpeg exited with code $exitCode"
                    AppLogger.e("FFmpeg download failed with code $exitCode")
                }
            } catch (e: CancellationException) {
                download.status = DownloadStatus.Cancelled
                download.process?.destroyForcibly()
                if (destFile.exists()) destFile.delete()
            } catch (t: Throwable) {
                AppLogger.e("FFmpeg process error", t)
                download.status = DownloadStatus.Failed
                download.error = t.message
            } finally {
                notifyUpdate()
            }
        }
    }

    fun cancelDownload(id: String) {
        activeDownloads[id]?.let { download ->
            download.status = DownloadStatus.Cancelled
            download.job?.cancel()
            download.process?.destroyForcibly()
            val file = File(download.localFilePath)
            if (file.exists()) file.delete()
            activeDownloads.remove(id)
            notifyUpdate()
        }
    }

    fun downloadEpisode(
        provider: com.lagradost.cloudstream3.MainAPI,
        data: com.lagradost.cloudstream3.LoadResponse,
        ep: com.lagradost.cloudstream3.Episode,
        onStatus: (String) -> Unit = {},
    ) {
        val downloadId = "${data.url}_${ep.data}"
        if (activeDownloads[downloadId]?.status == DownloadStatus.Downloading) return

        val placeholder = ActiveDownload(
            id = downloadId,
            showName = data.name,
            showUrl = data.url,
            episodeId = ep.data,
            episodeTitle = ep.name ?: "Episode ${ep.episode ?: 1}",
            season = ep.season,
            episode = ep.episode,
            posterUrl = ep.posterUrl ?: data.posterUrl,
            streamUrl = "",
            localFilePath = "",
            status = DownloadStatus.Queued,
        )
        activeDownloads[downloadId] = placeholder
        notifyUpdate()

        scope.launch {
            try {
                var patchedData = ep.data
                if (patchedData.startsWith("{") && patchedData.endsWith("}")) {
                    if (!patchedData.contains("\"title\"")) {
                        val titleStr = data.name.replace("\"", "\\\"")
                        patchedData = patchedData.replaceFirst("{", "{\"title\":\"$titleStr\",")
                    }
                    if (!patchedData.contains("\"tvtype\"")) {
                        patchedData = patchedData.replaceFirst("{", "{\"tvtype\":\"\",")
                    }
                }

                val defaultStreamKey = "default_stream_${provider.name}_${data.url}"
                val defaultStreamName = DesktopDataStore.getKey<String>(defaultStreamKey)

                val links = Collections.synchronizedList(mutableListOf<ExtractorLink>())
                var selectedLink: ExtractorLink? = null

                val scrapeJob = launch {
                    try {
                        provider.loadLinks(
                            data = patchedData,
                            isCasting = false,
                            subtitleCallback = {},
                            callback = { link ->
                                links.add(link)
                                if (!defaultStreamName.isNullOrBlank() && link.name.equals(defaultStreamName, ignoreCase = true)) {
                                    selectedLink = link
                                }
                            },
                        )
                    } catch (e: Exception) {
                        AppLogger.e("Error loading links for download", e)
                    }
                }

                val timeout = System.currentTimeMillis() + 8000L
                while (scrapeJob.isActive && System.currentTimeMillis() < timeout && selectedLink == null) {
                    delay(300)
                }
                if (scrapeJob.isActive) scrapeJob.cancel()

                val chosenLink = selectedLink ?: links.maxByOrNull { it.quality } ?: links.firstOrNull()
                if (chosenLink != null) {
                    startDownload(
                        showName = data.name,
                        showUrl = data.url,
                        episodeId = ep.data,
                        episodeTitle = ep.name ?: "Episode ${ep.episode ?: 1}",
                        season = ep.season,
                        episode = ep.episode,
                        posterUrl = ep.posterUrl ?: data.posterUrl,
                        link = chosenLink,
                    )
                    onStatus("Downloading ${chosenLink.name}...")
                } else {
                    placeholder.status = DownloadStatus.Failed
                    placeholder.error = "No playable stream links found"
                    notifyUpdate()
                    onStatus("No stream links found for download.")
                }
            } catch (e: Throwable) {
                AppLogger.e("Failed to trigger episode download", e)
                placeholder.status = DownloadStatus.Failed
                placeholder.error = e.message
                notifyUpdate()
            }
        }
    }

    private fun notifyUpdate() {
        _downloadsFlow.value = activeDownloads.values.toList()
    }
}
