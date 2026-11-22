package dev.inter129.mochikeys.settings

import android.content.Context

/** 키보드 설정. 키보드는 입력 칸이 열릴 때마다 다시 읽는다. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var sound: Boolean get() = p.getBoolean("sound", false); set(v) = p.edit().putBoolean("sound", v).apply()
    var soundVolume: Int get() = p.getInt("soundVolume", 55); set(v) = p.edit().putInt("soundVolume", v).apply()
    var haptic: Boolean get() = p.getBoolean("haptic", true); set(v) = p.edit().putBoolean("haptic", v).apply()
    var autoCap: Boolean get() = p.getBoolean("autoCap", true); set(v) = p.edit().putBoolean("autoCap", v).apply()
    var doubleSpace: Boolean get() = p.getBoolean("doubleSpace", true); set(v) = p.edit().putBoolean("doubleSpace", v).apply()
    /** 키 줄 하나의 높이(dp). 57이 기본. */
    var rowHeight: Int get() = p.getInt("rowHeight", 57); set(v) = p.edit().putInt("rowHeight", v).apply()
    var languages: List<String>
        get() = p.getString("languages", "en,ko,ja")!!.split(",").filter { it.isNotEmpty() }.ifEmpty { listOf("en") }
        set(v) = p.edit().putString("languages", v.joinToString(",")).apply()
}
