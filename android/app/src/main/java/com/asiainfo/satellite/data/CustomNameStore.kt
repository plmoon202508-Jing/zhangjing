package com.asiainfo.satellite.data

import android.content.Context

/**
 * 卫星自定义名称存储（有效期 1 天，过期自动清除）。
 * 存储格式：value = "<timestampMillis>|<name>"
 */
class CustomNameStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sat_custom_names", Context.MODE_PRIVATE)

    /** 取自定义名称；过期返回 null 并清除 */
    fun get(noradId: String): String? {
        val raw = prefs.getString(noradId, null) ?: return null
        val idx = raw.indexOf('|')
        if (idx <= 0) return null
        val ts = raw.substring(0, idx).toLongOrNull() ?: return null
        if (System.currentTimeMillis() - ts > TTL_MS) {
            prefs.edit().remove(noradId).apply()
            return null
        }
        return raw.substring(idx + 1)
    }

    /** 设置/清除自定义名称（空则清除） */
    fun set(noradId: String, name: String?) {
        val e = prefs.edit()
        if (name.isNullOrBlank()) e.remove(noradId)
        else e.putString(noradId, "${System.currentTimeMillis()}|${name.trim()}")
        e.apply()
    }

    companion object {
        private const val TTL_MS = 24 * 3600 * 1000L
    }
}
