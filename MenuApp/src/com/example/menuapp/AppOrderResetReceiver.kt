package com.example.menuapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AppOrderResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("MenuApp", "AppOrderResetReceiver: empfangen action=$action")
        when (action) {
            ACTION_RESET_APP_ORDER,
            ACTION_SET_STANDARD_ROTARY,
            ACTION_SET_MODE,
            ACTION_SET_ROTARY_MODE -> {
                AppOrderManager.resetOrder(context)
                context.sendBroadcast(Intent(ACTION_INTERNAL_ORDER_RESET))
            }
        }
    }

    companion object {
        const val ACTION_RESET_APP_ORDER = "com.example.menuapp.RESET_APP_ORDER"
        const val ACTION_SET_STANDARD_ROTARY = "com.example.knobservice.SET_STANDARD_ROTARY"
        const val ACTION_SET_MODE = "com.example.knobservice.SET_MODE"
        const val ACTION_SET_ROTARY_MODE = "com.example.knobservice.SET_ROTARY_MODE"
        const val ACTION_INTERNAL_ORDER_RESET = "com.example.menuapp.INTERNAL_ORDER_RESET"
    }
}

