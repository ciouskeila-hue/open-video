package io.github.devhyper.openvideoeditor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max

@Composable
fun TimelineView(
    timelineData: TimelineData,
    currentTimeMs: Long,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit,
    onClipChange: () -> Unit,
    onSplit: (String?, String?) -> Unit,
    onDelete: (String, String) -> Unit, // Add delete callback
    onAddTrack: () -> Unit,
    pixelsPerSecond: Float = 50f,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val totalWidth = (max(totalDurationMs, 60000L) / 1000f) * pixelsPerSecond

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E)) // Pr-like dark gray
    ) {
        // Time Ruler
        TimeRuler(
            durationMs = max(totalDurationMs, 60000L),
            pixelsPerSecond = pixelsPerSecond,
            scrollState = scrollState,
            modifier = Modifier.height(30.dp),
            onSeek = onSeek
        )

        // Tracks Container with Vertical Scroll
        Box(modifier = Modifier.weight(1f)) {
            val contentWidth = totalWidth

            // Horizontal Scroll Wrapper
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
               Box(modifier = Modifier.width(contentWidth.dp)) {
                    Column {
                        // Vertical Scroll for Tracks
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            itemsIndexed(timelineData.tracks) { index, track ->
                                TrackView(
                                    track = track,
                                    index = index,
                                    pixelsPerSecond = pixelsPerSecond,
                                    onClipChange = onClipChange,
                                    onSplit = onSplit,
                                    onDelete = onDelete
                                )
                            }

                            // Add Track Button at the bottom
                            item {
                                Button(
                                    onClick = onAddTrack,
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add Track", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Track", color = Color.White)
                                }
                            }
                        }
                    }

                    // Playhead Cursor (Overlay)
                    Playhead(
                        currentTimeMs = currentTimeMs,
                        pixelsPerSecond = pixelsPerSecond,
                        onSeek = onSeek
                    )
               }
            }
        }
    }
}

@Composable
fun TimeRuler(
    durationMs: Long,
    pixelsPerSecond: Float,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
            .horizontalScroll(scrollState)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val timeMs = ((offset.x / pixelsPerSecond) * 1000).toLong()
                    onSeek(timeMs)
                }
            }
    ) {
         val totalWidth = (durationMs / 1000f) * pixelsPerSecond
         Box(modifier = Modifier.width(totalWidth.dp)) {
            val seconds = (durationMs / 1000).toInt()
            Row {
                for (i in 0..seconds) {
                    Box(
                        modifier = Modifier
                            .width(pixelsPerSecond.dp)
                            .height(30.dp)
                            .border(width = 0.5.dp, color = Color.Gray)
                    ) {
                        if (i % 5 == 0) {
                            Text(
                                text = formatTime(i * 1000L),
                                color = Color.LightGray,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackView(
    track: Track,
    index: Int,
    pixelsPerSecond: Float,
    onClipChange: () -> Unit,
    onSplit: (String?, String?) -> Unit,
    onDelete: (String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp) // Taller tracks
            .padding(vertical = 2.dp)
            .background(Color(0xFF252525))
    ) {
        // Track Header (V1, V2, etc.)
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(Color(0xFF333333))
                .border(1.dp, Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "V${index + 1}",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Track Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            track.clips.forEach { clip ->
                ClipView(
                    clip = clip,
                    trackId = track.id,
                    pixelsPerSecond = pixelsPerSecond,
                    onClipChange = onClipChange,
                    onSplit = onSplit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
fun ClipView(
    clip: TimelineClip,
    trackId: String,
    pixelsPerSecond: Float,
    onClipChange: () -> Unit,
    onSplit: (String?, String?) -> Unit,
    onDelete: (String, String) -> Unit
) {
    var offsetX by remember { mutableFloatStateOf((clip.startTimeMs / 1000f) * pixelsPerSecond) }
    var widthPx by remember { mutableFloatStateOf((clip.durationMs / 1000f) * pixelsPerSecond) }
    var isSelected by remember { mutableStateOf(false) }

    // Sync state with data
    if (clip.startTimeMs != ((offsetX / pixelsPerSecond) * 1000).toLong()) {
         offsetX = (clip.startTimeMs / 1000f) * pixelsPerSecond
    }

    Box(
        modifier = Modifier
            .offset(x = offsetX.dp)
            .width(widthPx.dp)
            .fillMaxHeight()
            .zIndex(if (isSelected) 10f else 1f)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    offsetX += delta
                    if (offsetX < 0) offsetX = 0f
                    clip.startTimeMs = ((offsetX / pixelsPerSecond) * 1000).toLong()
                    onClipChange()
                }
            )
            .clickable { isSelected = !isSelected } // Toggle selection
            .background(
                color = when (clip.mediaTypeString) {
                    MediaType.VIDEO -> Color(0xFF3D85C6) // Pr Video Blue
                    MediaType.AUDIO -> Color(0xFF6AA84F) // Pr Audio Green
                    MediaType.TEXT -> Color(0xFFC27BA0)
                    MediaType.IMAGE -> Color(0xFFE69138)
                    else -> Color.Gray
                },
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.Black,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        // Label
        Text(
            text = clip.label.ifEmpty { "Clip" },
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(4.dp)
                .align(Alignment.CenterStart),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        // Context Actions (Visible on Selection)
        if (isSelected) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Split Button
                IconButton(
                    onClick = {
                        onSplit(trackId, clip.id)
                        isSelected = false // Deselect after split
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCut,
                        contentDescription = "Split",
                        tint = Color.Black,
                        modifier = Modifier.padding(4.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = {
                        onDelete(trackId, clip.id)
                        isSelected = false
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        // Right Handle (Trim)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(15.dp) // Wider handle
                .background(Color.White.copy(alpha = 0.3f))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        widthPx += delta
                        if (widthPx < 10f) widthPx = 10f
                        clip.durationMs = ((widthPx / pixelsPerSecond) * 1000).toLong()
                        onClipChange()
                    }
                )
        )
    }
}

@Composable
fun Playhead(
    currentTimeMs: Long,
    pixelsPerSecond: Float,
    onSeek: (Long) -> Unit
) {
    val offsetX = (currentTimeMs / 1000f) * pixelsPerSecond

    Box(
        modifier = Modifier
            .offset(x = offsetX.dp)
            .zIndex(100f)
            // Make the whole vertical line draggable? Or just the top handle?
            // User said "Needle... easy to touch".
            // Dragging the line anywhere is good.
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // We need to calculate new time based on delta.
                    // But delta is in pixels.
                    // New time = Current + (Delta / Scale) * 1000
                    val dt = (delta / pixelsPerSecond) * 1000
                    onSeek((currentTimeMs + dt).toLong().coerceAtLeast(0))
                }
            )
    ) {
        // Line
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.Red)
        )

        // Handle (Top)
        Box(
            modifier = Modifier
                .size(30.dp)
                .offset(x = (-14).dp, y = (-15).dp) // Center and move up slightly
                .background(Color.Red, CircleShape)
        )
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}