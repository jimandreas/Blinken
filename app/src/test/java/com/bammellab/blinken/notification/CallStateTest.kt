package com.bammellab.blinken.notification

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateTest {

    @Test
    fun `ringing mode suppresses the display`() {
        assertTrue(isCallAudioMode(AudioManager.MODE_RINGTONE))
    }

    @Test
    fun `in-call modes suppress the display`() {
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_CALL))
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_COMMUNICATION))
    }

    @Test
    fun `normal and invalid modes do not suppress the display`() {
        assertFalse(isCallAudioMode(AudioManager.MODE_NORMAL))
        assertFalse(isCallAudioMode(AudioManager.MODE_INVALID))
        assertFalse(isCallAudioMode(AudioManager.MODE_CURRENT))
    }
}
