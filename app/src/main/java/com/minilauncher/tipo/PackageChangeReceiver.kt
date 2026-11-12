package com.minilauncher.tipo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class PackageChangeReceiver(private val onChange: () -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        onChange()
    }

    companion object {
        fun filter(): IntentFilter {
            val f = IntentFilter()
            f.addAction(Intent.ACTION_PACKAGE_ADDED)
            f.addAction(Intent.ACTION_PACKAGE_REMOVED)
            f.addAction(Intent.ACTION_PACKAGE_CHANGED)
            f.addDataScheme("package")
            return f
        }
    }
}
