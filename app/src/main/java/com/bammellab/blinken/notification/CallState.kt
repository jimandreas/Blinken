package com.bammellab.blinken.notification

import android.content.Context
import android.media.AudioManager

// A ringing (or in-progress) phone call owns the lockscreen: the incoming-call UI is itself a
// full-screen intent over the keyguard, and the user is being asked to answer it right now.
// Blinken flashing its badge - or worse, sweeping a snake - across that is exactly the
// user-reported bug in issue #1. Suppress while the call is live; the missed-call notification
// that follows an unanswered ring is a perfectly ordinary allowlisted notification, so the
// "you missed a call" display still happens afterwards through the normal path.
//
// Deliberately read from AudioManager.getMode() rather than TelephonyManager.getCallState():
// the audio mode is public, permission-free, and covers VoIP/messaging-app calls
// (MODE_IN_COMMUNICATION) as well as cellular ones, whereas getCallState() requires
// READ_PHONE_STATE from API 31 on and only knows about telephony calls. Adding a
// runtime-permission prompt (and a scary-looking phone permission) to a notification-LED app is
// a poor trade for a check this cheap.
//
// Pure so the policy itself is unit-testable; the framework read stays a one-liner below.
fun isCallAudioMode(audioMode: Int): Boolean = when (audioMode) {
    AudioManager.MODE_RINGTONE,
    AudioManager.MODE_IN_CALL,
    AudioManager.MODE_IN_COMMUNICATION,
    -> true
    else -> false
}

fun isCallRingingOrActive(context: Context): Boolean {
    val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
    return isCallAudioMode(audioManager.mode)
}
