package com.jawad.tvlauncher

import android.app.admin.DeviceAdminReceiver

/** Device-admin hook so the sleep timer can put the screen to sleep (lockNow). */
class SleepAdminReceiver : DeviceAdminReceiver()
