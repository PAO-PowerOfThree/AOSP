package com.example.testui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("UpdateReceiver", "Received intent: ${intent.action}")

        if (Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) {
            Log.i("UpdateReceiver", "App updated → Restarting")
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)
        } else {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            Log.i("UpdateReceiver", "Install status: $status, message: $statusMessage")
            if (status == PackageInstaller.STATUS_SUCCESS) {
                Log.i("UpdateReceiver", "Install success → Restarting")
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
            }
        }
    }
}