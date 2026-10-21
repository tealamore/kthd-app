package com.tealamore.kthd

import android.content.Context
import androidx.core.content.edit

object ConsentManager {
    private const val PREFS_NAME = "kthd_consent_prefs"
    private const val KEY_HAS_CONSENTED = "has_consented"

    fun hasConsented(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_CONSENTED, false)
    }

    fun setConsented(context: Context, consented: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_HAS_CONSENTED, consented) }
    }
}
