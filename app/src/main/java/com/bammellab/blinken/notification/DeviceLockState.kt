package com.bammellab.blinken.notification

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

// isKeyguardLocked alone can misreport while a setShowWhenLocked activity (FlashActivity) is
// occluding the keyguard, so "locked" here is the union of both KeyguardManager signals:
// isKeyguardLocked (keyguard showing, secure or swipe-only) and isDeviceLocked (credentials
// still required, which stays true through occlusion on secure keyguards). Either one being
// true means the user has not actually unlocked the device.
fun isKeyguardShowingOrDeviceLocked(context: Context): Boolean {
    val keyguardManager = context.getSystemService(KeyguardManager::class.java) ?: return false
    return keyguardManager.isKeyguardLocked || keyguardManager.isDeviceLocked
}

// The flash/snake display is a lockscreen-only stand-in for a hardware LED: while the user is
// actively using the unlocked device, notifications are already visible normally, so triggering
// the display would only surface the internal full-screen-intent notification as a heads-up
// (the OS auto-launches a full-screen intent only while locked; unlocked it degrades to a
// shade/heads-up notification). "Active unlocked use" deliberately requires the screen to be
// interactive AND the keyguard dismissed - a screen that is off still counts as showable even if
// the keyguard hasn't engaged yet (lock-after-timeout delay, Smart Lock), since the nightstand
// scenario the display exists for doesn't depend on the keyguard actually being up.
fun isDeviceInActiveUnlockedUse(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java)
    val interactive = powerManager?.isInteractive ?: true
    return interactive && !isKeyguardShowingOrDeviceLocked(context)
}
