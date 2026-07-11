package com.jimandreas.blinken.notification

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

// "Charging" here means any power source plugged in (AC/USB/wireless), not the stricter
// BatteryManager.isCharging() (which can read false once the battery is topped off) - this
// matches the nightstand/dock scenario the continuous "snake" display is built for: the device
// is plugged in and expected to stay on, whether or not it's still actively drawing current.
fun isDeviceCharging(context: Context): Boolean {
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
    return plugged != 0
}
