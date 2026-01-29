package io.github.devhyper.openvideoeditor.timeline

import org.junit.Test
import org.junit.Assert.*

class TimelineLogicTest {

    @Test
    fun testTrackSorting() {
        val track = Track(mediaType = MediaType.VIDEO)
        val clip1 = TimelineClip(uri = "uri1", mediaTypeString = MediaType.VIDEO, startTimeMs = 5000, durationMs = 1000)
        val clip2 = TimelineClip(uri = "uri2", mediaTypeString = MediaType.VIDEO, startTimeMs = 1000, durationMs = 1000)

        track.clips.add(clip1)
        track.clips.add(clip2)

        val sorted = track.clips.sortedBy { it.startTimeMs }
        assertEquals("uri2", sorted[0].uri)
        assertEquals("uri1", sorted[1].uri)
    }

    @Test
    fun testDurationCalculation() {
        val clip = TimelineClip(uri = "test", mediaTypeString = MediaType.VIDEO, startTimeMs = 0, durationMs = 10000)
        // Simulate trim
        clip.durationMs = 5000
        assertEquals(5000L, clip.durationMs)
    }

    @Test
    fun testSplitLogic() {
        // Setup: A 10s clip starting at 0s
        val track = Track(mediaType = MediaType.VIDEO)
        val originalClip = TimelineClip(
            uri = "source.mp4",
            mediaTypeString = MediaType.VIDEO,
            startTimeMs = 0,
            durationMs = 10000,
            offsetMs = 0
        )
        track.clips.add(originalClip)

        // Action: Split at 3s (3000ms)
        val splitPoint = 3000L

        // Simulate the logic inside TransformManager.splitClipAtCurrentPosition
        val clipToSplit = track.clips.find { splitPoint > it.startTimeMs && splitPoint < (it.startTimeMs + it.durationMs) }
        assertNotNull(clipToSplit)

        if (clipToSplit != null) {
            val relativeSplitPoint = splitPoint - clipToSplit.startTimeMs
            val originalDuration = clipToSplit.durationMs
            val originalOffset = clipToSplit.offsetMs

            // 1. Update Left
            clipToSplit.durationMs = relativeSplitPoint

            // 2. Create Right
            val newClip = clipToSplit.copy(
                id = java.util.UUID.randomUUID().toString(),
                startTimeMs = splitPoint,
                durationMs = originalDuration - relativeSplitPoint,
                offsetMs = originalOffset + relativeSplitPoint,
                label = clipToSplit.label + " (Split)"
            )
            track.clips.add(newClip)
        }

        // Verify Left Clip
        assertEquals(3000L, originalClip.durationMs) // Should be shortened to 3s

        // Verify Right Clip
        val rightClip = track.clips.find { it.startTimeMs == 3000L }
        assertNotNull(rightClip)
        assertEquals(7000L, rightClip?.durationMs) // 10s - 3s = 7s duration
        assertEquals(3000L, rightClip?.offsetMs)   // Offset should be 3s (skip first 3s of source)
    }
}
