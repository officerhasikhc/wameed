package com.wameed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * يستقبل أحداث النظام لتشغيل خدمة وميض في الخلفية.
 * مثلاً: عند فتح قفل الشاشة، نتأكد من أن الخدمة تعمل لضمان سرعة الاتصال.
 */
class WameedBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("WameedReceiver", "System event: ${intent.action} - Starting service")
            WameedConnectionService.start(context)
        }
    }
}
