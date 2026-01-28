package io.github.devhyper.openvideoeditor.videoeditor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItemSequence
import io.github.devhyper.openvideoeditor.misc.AppLogger
import io.github.devhyper.openvideoeditor.timeline.MediaType
import io.github.devhyper.openvideoeditor.timeline.TimelineData
import io.github.devhyper.openvideoeditor.timeline.TimelineClip
import androidx.media3.transformer.Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
import androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR
import androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
import androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED
import androidx.media3.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.SessionState
import io.github.devhyper.openvideoeditor.misc.PROJECT_FILE_EXT
import io.github.devhyper.openvideoeditor.misc.getFileNameFromUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.json.JSONArray
import org.json.JSONObject


typealias Trim = Pair<Long, Long>
typealias ImageConstructor = () -> ImageVector
typealias EffectConstructor = () -> Effect
typealias Editor = @Composable (MutableStateFlow<EffectConstructor?>) -> Unit

class EffectDialogSetting(
    val key: String,
    val stringResId: Int,
    val textfieldValidation: ((String) -> String)? = null,
    val dropdownOptions: MutableList<String>? = null
) {
    var selection = ""
}

class ExportSettings {
    var exportAudio = true
    var exportVideo = true
    var hdrMode: Int = HDR_MODE_KEEP_HDR
    var audioMimeType: String? = null
    var videoMimeType: String? = null
    var framerate: Float = 0F
    var speed: Float = 0F
    var outputPath: String = ""
    var losslessCut: Boolean = false

    /*
    fun log() {
        Log.i(
            "open-video-editor",
            "\nexportVideo: $exportVideo\nexportAudio: $exportAudio\nhdrMode: $hdrMode\naudioMimeType: $audioMimeType\nvideoMimeType: $videoMimeType\noutputPath: $outputPath"
        )
    }
     */

    fun setMediaToExportString(string: String) {
        when (string) {
            "Video and Audio" -> {
                exportVideo = true; exportAudio = true; }

            "Video only" -> {
                exportVideo = true; exportAudio = false; }

            "Audio only" -> {
                exportVideo = false; exportAudio = true; }
        }
    }

    fun setHdrModeString(string: String) {
        when (string) {
            "Keep HDR" -> {
                hdrMode = HDR_MODE_KEEP_HDR
            }

            "HDR as SDR" -> {
                hdrMode = HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
            }

            "HDR to SDR (Mediacodec)" -> {
                hdrMode = HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
            }

            "HDR to SDR (OpenGL)" -> {
                hdrMode = HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            }
        }
    }

    fun setAudioMimeTypeString(string: String) {
        audioMimeType = if (string == "Original") {
            null
        } else {
            string
        }
    }

    fun setVideoMimeTypeString(string: String) {
        videoMimeType = if (string == "Original") {
            null
        } else {
            string
        }
    }
}

fun getMediaToExportStrings(): ImmutableList<String> {
    return persistentListOf("Video and Audio", "Video only", "Audio only")
}

fun getHdrModesStrings(): ImmutableList<String> {
    return persistentListOf(
        "Keep HDR",
        "HDR as SDR",
        "HDR to SDR (Mediacodec)",
        "HDR to SDR (OpenGL)"
    )
}

fun getAudioMimeTypesStrings(): ImmutableList<String> {
    return persistentListOf(
        "Original",
        MimeTypes.AUDIO_AAC,
        MimeTypes.AUDIO_AMR_NB,
        MimeTypes.AUDIO_AMR_WB
    )
}

fun getVideoMimeTypesStrings(): ImmutableList<String> {
    return persistentListOf(
        "Original",
        MimeTypes.VIDEO_H263,
        MimeTypes.VIDEO_H264,
        MimeTypes.VIDEO_H265,
        MimeTypes.VIDEO_MP4V
    )
}

class DialogUserEffect(
    val stringResId: Int,
    val icon: ImageConstructor,
    val args: PersistentList<EffectDialogSetting>,
    val callback: (Map<String, String>) -> EffectConstructor
)

class OnVideoUserEffect(
    val stringResId: Int,
    val icon: ImageConstructor,
    val editor: Editor,
) {
    var callback: (EffectConstructor) -> Unit = {}

    private val effect = MutableStateFlow<EffectConstructor?>(null)

    fun runCallback() {
        effect.value?.let { callback(it) }
    }

    @Composable
    fun Editor() {
        editor(effect)
    }
}

class UserEffect(
    val stringResId: Int,
    val icon: ImageConstructor,
    val effect: EffectConstructor
) : java.io.Serializable

data class ProjectData(
    val uri: String,

    val videoEffects: MutableList<UserEffect> = mutableListOf(),
    val audioProcessors: MutableList<AudioProcessor> = mutableListOf(),
    val mediaTrims: MutableList<Trim> = mutableListOf(),
    var timelineData: TimelineData = TimelineData()
) : java.io.Serializable {
    companion object {
        fun read(uri: String, context: Context): ProjectData? {
            var projectData: ProjectData? = null
            context.contentResolver.openInputStream(uri.toUri())?.let {
                val input = ObjectInputStream(it)
                projectData = input.readObject() as ProjectData?
                input.close()
            }
            return projectData
        }
    }

    fun write(uri: String, context: Context) {
        context.contentResolver.openOutputStream(uri.toUri())?.let {
            val output = ObjectOutputStream(it)
            output.writeObject(this)
            output.close()
        }
    }
}

class TransformManager {
    lateinit var player: ExoPlayer

    private var hasInitialized = false

    private var transformer: Transformer? = null

    private lateinit var originalMedia: MediaItem

    private lateinit var trimmedMedia: MediaItem

    lateinit var projectData: ProjectData

    private var blackImagePath: String = ""

    fun init(
        exoPlayer: ExoPlayer,
        uri: String,
        context: Context,
        viewModel: VideoEditorViewModel,
        requestVideoPermission: ActivityResultLauncher<String>
    ) {
        if (hasInitialized) {
            if (exoPlayer != player) {
                if (player.isCommandAvailable(Player.COMMAND_RELEASE)) {
                    player.release()
                }
                player = exoPlayer
            }
        } else {
            player = exoPlayer
            projectData = if (getFileNameFromUri(context, uri.toUri()).substringAfterLast(
                    '.',
                    ""
                ).substringBeforeLast(' ') == PROJECT_FILE_EXT
            ) {
                ProjectData.read(uri, context) ?: ProjectData(uri)
            } else {
                ProjectData(uri)
            }
            var projectSavingSupported = false
            if (requestPersistablePermissions(
                    context,
                    uri.toUri(),
                    requestVideoPermission
                )
            ) {
                projectSavingSupported = true
            }
            viewModel.setProjectSavingSupported(projectSavingSupported)
            hasInitialized = true
        }
        originalMedia = MediaItem.fromUri(projectData.uri)
        trimmedMedia = MediaItem.fromUri(projectData.uri)

        blackImagePath = createBlackImage(context)

        if (projectData.timelineData.tracks.isEmpty()) {
             player.setMediaItem(originalMedia)
             player.prepare()
             player.playWhenReady = false // Start paused
        } else {
             updateMediaTrims(context)
        }
    }

    private fun createBlackImage(context: Context): String {
        val file = java.io.File(context.cacheDir, "black_placeholder.png")
        if (!file.exists()) {
            val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLACK)
            val fos = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
        }
        return file.absolutePath
    }

    private fun requestPersistablePermissions(
        context: Context,
        uri: Uri,
        requestVideoPermission: ActivityResultLauncher<String>
    ): Boolean {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                    requestVideoPermission.launch(Manifest.permission.READ_MEDIA_VIDEO)
                }
            } else {
                if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestVideoPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            return false
        }
        return true
    }

    private fun getEffectArray(): MutableList<Effect> {
        val effectArray = mutableListOf<Effect>()
        for (userEffect in projectData.videoEffects) {
            effectArray.add(userEffect.effect())
        }
        return effectArray
    }

    fun getMergedTrim(): Trim? {
        if (projectData.mediaTrims.isNotEmpty()) {
            var currentPair = projectData.mediaTrims[0]

            if (projectData.mediaTrims.size > 1) {
                for (i in 1 until projectData.mediaTrims.size) {
                    val cutStart =
                        currentPair.first - (projectData.mediaTrims[i - 1].first - projectData.mediaTrims[i].first)
                    val cutEnd =
                        currentPair.second - (projectData.mediaTrims[i - 1].second - projectData.mediaTrims[i].second)
                    currentPair = Trim(cutStart, cutEnd)
                }
            }

            return currentPair
        }
        return null
    }

    fun clearMediaTrims(context: Context) {
        projectData.mediaTrims.clear()
        updateMediaTrims(context)
    }

    fun addVideoEffect(effect: UserEffect, context: Context) {
        projectData.videoEffects.add(effect)
        updateVideoEffects() // Effect update doesn't need context usually, but if we rebuild playlist...
        updateMediaTrims(context)
    }

    fun addAudioProcessor(processor: AudioProcessor) {
        projectData.audioProcessors.add(processor)
        updateAudioProcessors()
    }

    fun addMediaTrim(trim: Trim, context: Context) {
        if (projectData.mediaTrims.isNotEmpty() && trim == projectData.mediaTrims.last()) {
            return
        }
        projectData.mediaTrims.add(trim)
        updateMediaTrims(context)
    }

    fun removeVideoEffect(effect: UserEffect, context: Context) {
        projectData.videoEffects.remove(effect)
        updateMediaTrims(context)
    }

    fun removeAudioProcessor(processor: AudioProcessor) {
        projectData.audioProcessors.remove(processor)
        updateAudioProcessors()
    }

    fun removeMediaTrim(trim: Trim, context: Context) {
        projectData.mediaTrims.remove(trim)
        updateMediaTrims(context)
    }

    private fun updateVideoEffects() {
        // Effects are applied via setVideoEffects, which persists across playlist items?
        // Yes, typically.
        player.setVideoEffects(getEffectArray())
    }

    private fun updateAudioProcessors() {
        // TODO
    }

    private fun rebuildMediaTrims() {
        val trim = getMergedTrim()
        trimmedMedia = if (trim != null) {
            val clipConfig = ClippingConfiguration.Builder().setStartPositionMs(trim.first)
                .setEndPositionMs(trim.second).build()
            originalMedia.buildUpon().setClippingConfiguration(clipConfig).build()
        } else {
            originalMedia
        }
    }

    fun seekToGlobalPosition(globalPositionMs: Long) {
        if (projectData.timelineData.tracks.isEmpty()) return

        // Find which clip contains this position
        // We iterate through the playlist (which corresponds to sorted timeline clips + gaps)
        // Wait, updatePlayerFromTimeline constructs a playlist.
        // We need to match that structure.

        // Simpler approach: Iterate through our TimelineData track 0 clips (and calculated gaps)
        // to find target.
        // Or simpler: We know the playlist index structure if we kept track of it.
        // But we didn't.

        // Let's reconstruct the mapping logic used in updatePlayerFromTimeline.
        val videoTracks = projectData.timelineData.tracks.filter { it.mediaType == io.github.devhyper.openvideoeditor.timeline.MediaType.VIDEO } // Using MediaType directly from import if available, or string literal "VIDEO" if that's what we aligned on.
        // We aligned on "VIDEO" string in TimelineData.kt?
        // Let's check TimelineData.kt content. It uses object MediaType { const val VIDEO = "VIDEO" }.
        // So I can use "VIDEO" or MediaType.VIDEO. I'll use "VIDEO" to be safe.

        if (videoTracks.isEmpty()) return
        val mainTrack = videoTracks[0]
        val sortedClips = mainTrack.clips.sortedBy { it.startTimeMs }

        var currentOffset = 0L
        // We need to account for GAPS too, because updatePlayerFromTimeline inserts gap items!

        // This is tricky. The playlist indices = clips + gaps.
        // I need to iterate exactly like updatePlayerFromTimeline does.

        var playlistIndex = 0
        var accumulatedTime = 0L
        var lastEndTimeMs = 0L

        for (clip in sortedClips) {
            // Check Gap
            if (clip.startTimeMs > lastEndTimeMs) {
                val gapDuration = clip.startTimeMs - lastEndTimeMs
                if (gapDuration > 0) {
                    // There is a gap item at playlistIndex
                    if (globalPositionMs < accumulatedTime + gapDuration) {
                        // Target is in this gap
                        player.seekTo(playlistIndex, globalPositionMs - accumulatedTime)
                        return
                    }
                    accumulatedTime += gapDuration
                    playlistIndex++
                }
            }

            // Check Clip
            if (globalPositionMs < accumulatedTime + clip.durationMs) {
                // Target is in this clip
                player.seekTo(playlistIndex, globalPositionMs - accumulatedTime)
                return
            }
            accumulatedTime += clip.durationMs
            playlistIndex++
            lastEndTimeMs = clip.startTimeMs + clip.durationMs
        }

        // If we are past the end, seek to end of last item
        if (playlistIndex > 0) {
             player.seekTo(playlistIndex - 1, player.currentTimeline.getWindow(playlistIndex - 1, androidx.media3.common.Timeline.Window()).durationMs)
        }
    }

    fun getCurrentGlobalPosition(): Long {
        if (projectData.timelineData.tracks.isEmpty()) return 0L

        val currentWindowIndex = player.currentMediaItemIndex
        val currentWindowPos = player.currentPosition

        // We need to sum up durations of all items BEFORE currentWindowIndex
        var globalPos = 0L

        // Iterate timeline/playlist structure again?
        // Or simpler: ask Player for duration of previous windows.

        val timeline = player.currentTimeline
        if (timeline.isEmpty) return 0L

        for (i in 0 until currentWindowIndex) {
            val window = androidx.media3.common.Timeline.Window()
            timeline.getWindow(i, window)
            globalPos += window.durationMs
        }

        return globalPos + currentWindowPos
    }

    fun getTotalGlobalDuration(): Long {
        // Calculate based on last clip end time
        val videoTracks = projectData.timelineData.tracks.filter { it.mediaType == "VIDEO" }
        if (videoTracks.isEmpty()) return 0L

        val mainTrack = videoTracks[0]
        val sortedClips = mainTrack.clips.sortedBy { it.startTimeMs }
        if (sortedClips.isEmpty()) return 0L

        val lastClip = sortedClips.last()
        return lastClip.startTimeMs + lastClip.durationMs
    }

    private fun updatePlayerFromTimeline(context: Context) {
        val videoTracks = projectData.timelineData.tracks.filter { it.mediaType == "VIDEO" }

        if (videoTracks.isEmpty() || videoTracks.all { it.clips.isEmpty() }) {
            player.clearMediaItems()
            return
        }

        val mainTrack = videoTracks[0]
        val sortedClips = mainTrack.clips.sortedBy { it.startTimeMs }

        if (sortedClips.isEmpty()) {
            player.clearMediaItems()
            return
        }

        val mediaItems = mutableListOf<MediaItem>()
        var lastEndTimeMs = 0L

        if (blackImagePath.isEmpty()) {
            blackImagePath = createBlackImage(context)
        }

        for (clip in sortedClips) {
            // Gap handling
            if (clip.startTimeMs > lastEndTimeMs) {
                val gapDuration = clip.startTimeMs - lastEndTimeMs
                if (gapDuration > 0) {
                    val gapItem = MediaItem.Builder()
                        .setUri("file://$blackImagePath")
                        .setImageDurationMs(gapDuration)
                        .setMimeType(MimeTypes.IMAGE_PNG)
                        .build()
                    mediaItems.add(gapItem)
                }
            }

            val clipConfig = ClippingConfiguration.Builder()
                .setStartPositionMs(clip.offsetMs)
                .setEndPositionMs(clip.offsetMs + clip.durationMs)
                .build()
            val item = MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(clipConfig)
                .build()
            mediaItems.add(item)

            lastEndTimeMs = clip.startTimeMs + clip.durationMs
        }

        player.setMediaItems(mediaItems)
        player.prepare()
    }

    fun updateMediaTrims(context: Context) {
        if (projectData.timelineData.tracks.isNotEmpty()) {
            updatePlayerFromTimeline(context)
            return
        }

        rebuildMediaTrims()

        player.apply {
            stop()
            setMediaItem(trimmedMedia)
            setVideoEffects(getEffectArray())
            prepare()
        }
    }

    private fun ffmpegLosslessCut(
        context: Context,
        trim: Trim,
        outputPath: String,
        audioFallback: Boolean,
        onFFmpegError: () -> Unit
    ) {
        val ffmpegInputPath =
            FFmpegKitConfig.getSafParameterForRead(context, projectData.uri.toUri())
        val ffmpegOutputPath = FFmpegKitConfig.getSafParameterForWrite(context, outputPath.toUri())
        val audioCodec = if (audioFallback) "aac" else "copy"
        FFmpegKit.executeAsync(
            "-i $ffmpegInputPath -ss ${trim.first}ms -to ${trim.second}ms -c:v copy -c:a $audioCodec $ffmpegOutputPath"
        ) {
            val fd = context.contentResolver.openAssetFileDescriptor(outputPath.toUri(), "r")
            if (fd != null) {
                val fileSize = fd.length
                fd.close()
                if (fileSize != 0L) {
                    return@executeAsync
                }
            }
            if (audioFallback) {
                onFFmpegError()
            } else {
                ffmpegLosslessCut(context, trim, outputPath, true, onFFmpegError)
            }
        }
    }

    @SuppressLint("Recycle")
    fun export(
        context: Context,
        exportSettings: ExportSettings,
        transformerListener: Transformer.Listener,
        onFFmpegError: () -> Unit
    ) {
        player.release()
        val outputPath = exportSettings.outputPath

        if (projectData.timelineData.tracks.isNotEmpty()) {
            exportTimeline(context, exportSettings, transformerListener, outputPath)
            return
        }

        if (exportSettings.losslessCut) {
            val trim = getMergedTrim()
            if (trim != null) {
                ffmpegLosslessCut(context, trim, outputPath, false, onFFmpegError)
            }
        } else {
            val fd =
                context.contentResolver.openFileDescriptor(
                    outputPath.toUri(),
                    "rw"
                )?.fileDescriptor
            val effectArray = getEffectArray()
            effectArray.apply {
                if (exportSettings.speed > 0) {
                    add(SpeedChangeEffect(exportSettings.speed))
                }
                if (exportSettings.framerate > 0) {
                    add(FrameDropEffect.createDefaultFrameDropEffect(exportSettings.framerate))
                }
            }
            val editedMediaItem = EditedMediaItem.Builder(trimmedMedia)
                .setEffects(Effects(projectData.audioProcessors, effectArray))
                .setRemoveAudio(!exportSettings.exportAudio)
                .setRemoveVideo(!exportSettings.exportVideo)
                .build()
            transformer = Transformer.Builder(context)
                .setTransformationRequest(
                    TransformationRequest.Builder()
                        .setHdrMode(exportSettings.hdrMode)
                        .setAudioMimeType(exportSettings.audioMimeType)
                        .setVideoMimeType(exportSettings.videoMimeType)
                        .build()
                )
                .setMuxerFactory(CustomMuxer.Factory(fd))
                .addListener(transformerListener)
                .build()
            if (fd != null) {
                transformer!!.start(editedMediaItem, "")
            } else {
                transformer!!.start(editedMediaItem, outputPath)
            }
        }
    }

    private fun exportTimeline(
        context: Context,
        exportSettings: ExportSettings,
        transformerListener: Transformer.Listener,
        outputPath: String
    ) {
        val sequences = mutableListOf<EditedMediaItemSequence>()

        for (track in projectData.timelineData.tracks) {
            val items = mutableListOf<EditedMediaItem>()
            val sortedClips = track.clips.sortedBy { it.startTimeMs }

            val effectArray = getEffectArray()
            if (exportSettings.speed > 0) {
                effectArray.add(SpeedChangeEffect(exportSettings.speed))
            }
            if (exportSettings.framerate > 0) {
                effectArray.add(FrameDropEffect.createDefaultFrameDropEffect(exportSettings.framerate))
            }

            for (clip in sortedClips) {
                try {
                    val mediaItem = MediaItem.fromUri(clip.uri)
                    val clipConfig = ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.offsetMs)
                        .setEndPositionMs(clip.offsetMs + clip.durationMs)
                        .build()
                    val clippedMedia = mediaItem.buildUpon().setClippingConfiguration(clipConfig).build()

                    val editedItemBuilder = EditedMediaItem.Builder(clippedMedia)

                    if (track.mediaType == "VIDEO") {
                        editedItemBuilder.setEffects(Effects(projectData.audioProcessors, effectArray))
                        editedItemBuilder.setRemoveAudio(!exportSettings.exportAudio || track.isMuted)
                        editedItemBuilder.setRemoveVideo(!exportSettings.exportVideo)
                    } else if (track.mediaType == "AUDIO") {
                        editedItemBuilder.setRemoveVideo(true)
                        if (track.isMuted || !exportSettings.exportAudio) {
                            editedItemBuilder.setRemoveAudio(true)
                        }
                    }

                    items.add(editedItemBuilder.build())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (items.isNotEmpty()) {
                sequences.add(EditedMediaItemSequence(items))
            }
        }

        if (sequences.isEmpty()) {
            return
        }

        val composition = Composition.Builder(sequences)
            .setEffects(Effects(projectData.audioProcessors, getEffectArray()))
            .build()

        val fd = context.contentResolver.openFileDescriptor(outputPath.toUri(), "rw")?.fileDescriptor

        transformer = Transformer.Builder(context)
            .setTransformationRequest(
                TransformationRequest.Builder()
                    .setHdrMode(exportSettings.hdrMode)
                    .setAudioMimeType(exportSettings.audioMimeType)
                    .setVideoMimeType(exportSettings.videoMimeType)
                    .build()
            )
            .setMuxerFactory(CustomMuxer.Factory(fd))
            .addListener(transformerListener)
            .build()

        if (fd != null) {
            transformer!!.start(composition, "")
        } else {
            transformer!!.start(composition, outputPath)
        }
    }

    fun cancel() {
        FFmpegKit.cancel()
        transformer?.cancel()
    }

    fun getProgress(): Float {
        val ffmpegSessions = FFmpegKit.listSessions()
        return if (ffmpegSessions.isNotEmpty()) {
            val sessionState = ffmpegSessions.last().state
            return when (sessionState) {
                SessionState.COMPLETED -> 1F
                SessionState.RUNNING -> 0.5F
                SessionState.CREATED -> 0F
                else -> -1F
            }
        } else {
            val progressHolder = ProgressHolder()
            when (transformer?.getProgress(progressHolder)) {
                PROGRESS_STATE_UNAVAILABLE -> -1F
                PROGRESS_STATE_NOT_STARTED -> 1F
                else -> progressHolder.progress.toFloat() / 100F
            }
        }
    }

    fun addTrack(mediaType: String = "VIDEO") {
        val newTrack = io.github.devhyper.openvideoeditor.timeline.Track(
            mediaType = mediaType,
            clips = mutableListOf()
        )
        projectData.timelineData.tracks.add(newTrack)
    }

    fun deleteClip(trackId: String, clipId: String, context: Context) {
        AppLogger.i("Action: Delete Clip Requested. Track=$trackId, Clip=$clipId")
        val track = projectData.timelineData.tracks.find { it.id == trackId }
        if (track != null) {
            val clipToRemove = track.clips.find { it.id == clipId }
            if (clipToRemove != null) {
                track.clips.remove(clipToRemove)
                AppLogger.i("Deleted clip ${clipToRemove.label}")
                updateMediaTrims(context)
            }
        }
    }

    fun splitClipAtCurrentPosition(context: Context, targetTrackId: String? = null, targetClipId: String? = null) {
        val currentPosition = player.currentPosition
        AppLogger.i("Action: Split Clip Requested at ${currentPosition}ms. Target: Track=$targetTrackId, Clip=$targetClipId")

        var splitOccurred = false

        for (track in projectData.timelineData.tracks) {
            if (targetTrackId != null && track.id != targetTrackId) continue

            val clipToSplit = track.clips.find {
                if (targetClipId != null && it.id != targetClipId) return@find false
                currentPosition > it.startTimeMs && currentPosition < (it.startTimeMs + it.durationMs)
            }

            if (clipToSplit != null) {
                val relativeSplitPoint = currentPosition - clipToSplit.startTimeMs
                val originalDuration = clipToSplit.durationMs
                val originalOffset = clipToSplit.offsetMs

                clipToSplit.durationMs = relativeSplitPoint

                val newClip = clipToSplit.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    startTimeMs = currentPosition,
                    durationMs = originalDuration - relativeSplitPoint,
                    offsetMs = originalOffset + relativeSplitPoint,
                    label = clipToSplit.label
                )

                track.clips.add(newClip)
                track.clips.sortBy { it.startTimeMs }

                splitOccurred = true
            }
        }

        if (splitOccurred) {
            AppLogger.i("Split successful. Refreshing player.")
            updateMediaTrims(context)
        }
    }

    fun importScript(jsonString: String, videoUri: String, context: Context) {
        try {
            val jsonArray = JSONArray(jsonString)
            val newClips = mutableListOf<io.github.devhyper.openvideoeditor.timeline.TimelineClip>()
            var currentStartTime = 0L

            for (i in 0 until jsonArray.length()) {
                val scene = jsonArray.getJSONObject(i)
                val fragments = scene.optJSONArray("fragments")

                if (fragments != null) {
                    for (j in 0 until fragments.length()) {
                        val frag = fragments.getJSONObject(j)
                        val startStr = frag.getString("start")
                        val endStr = frag.getString("end")
                        val speed = frag.optDouble("speed", 1.0).toFloat()

                        val startMs = parseTime(startStr)
                        val endMs = parseTime(endStr)
                        val durationMs = (endMs - startMs).coerceAtLeast(100L)

                        val clip = io.github.devhyper.openvideoeditor.timeline.TimelineClip(
                            uri = videoUri,
                            startTimeMs = currentStartTime,
                            durationMs = durationMs,
                            offsetMs = startMs,
                            label = "Scene $i Frag $j",
                            playbackSpeed = speed,
                            mediaTypeString = "VIDEO"
                        )

                        newClips.add(clip)
                        currentStartTime += clip.durationMs
                    }
                }
            }

            if (newClips.isNotEmpty()) {
                projectData.timelineData.tracks.clear()
                val videoTrack = io.github.devhyper.openvideoeditor.timeline.Track(
                    java.util.UUID.randomUUID().toString(),
                    "VIDEO",
                    newClips
                )
                projectData.timelineData.tracks.add(videoTrack)
                projectData.timelineData.tracks.add(io.github.devhyper.openvideoeditor.timeline.Track(mediaType = "AUDIO"))

                projectData.timelineData.totalDurationMs = currentStartTime
                updateMediaTrims(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppLogger.e("Script Import Failed: $e")
        }
    }

    private fun parseTime(timeStr: String): Long {
        val parts = timeStr.split(":")
        var h = 0
        var m = 0
        var s = 0.0
        if (parts.size == 3) {
            h = parts[0].toInt()
            m = parts[1].toInt()
            s = parts[2].toDouble()
        }
        return (h * 3600000 + m * 60000 + s * 1000).toLong()
    }
}