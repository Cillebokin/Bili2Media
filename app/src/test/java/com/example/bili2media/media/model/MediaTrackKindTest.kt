package com.example.bili2media.media.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTrackKindTest {
    @Test
    fun fromMime_classifiesVideoAudioAndOtherTracks() {
        assertEquals(MediaTrackKind.VIDEO, MediaTrackKind.fromMime("video/avc"))
        assertEquals(MediaTrackKind.VIDEO, MediaTrackKind.fromMime("VIDEO/HEVC"))
        assertEquals(MediaTrackKind.AUDIO, MediaTrackKind.fromMime("audio/mp4a-latm"))
        assertEquals(MediaTrackKind.OTHER, MediaTrackKind.fromMime("application/id3"))
        assertEquals(MediaTrackKind.OTHER, MediaTrackKind.fromMime("  "))
    }
}
