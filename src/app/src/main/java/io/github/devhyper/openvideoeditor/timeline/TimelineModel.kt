package io.github.devhyper.openvideoeditor.timeline

import java.util.UUID

object MediaType {
    const val VIDEO = "VIDEO"
    const val AUDIO = "AUDIO"
    const val TEXT = "TEXT"
    const val IMAGE = "IMAGE"
}

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    var startTimeMs: Long,
    var durationMs: Long,
    var offsetMs: Long = 0,
    var label: String = "",
    var playbackSpeed: Float = 1.0f,
    val mediaTypeString: String = MediaType.VIDEO
) : java.io.Serializable

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val mediaType: String,
    val clips: MutableList<TimelineClip> = mutableListOf(),
    var isMuted: Boolean = false,
    var isLocked: Boolean = false
) : java.io.Serializable

data class TimelineData(
    val tracks: MutableList<Track> = mutableListOf(),
    var totalDurationMs: Long = 0
) : java.io.Serializable