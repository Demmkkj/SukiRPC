package dev.nuvio.presence

import android.content.SharedPreferences

internal fun MediaFormat.save(prefs: SharedPreferences) {
    val editor = prefs.edit()
    settings().forEach { (key, value) -> editor.putString(key, value) }
    editor.apply()
}

internal fun readPrivacy(prefs: SharedPreferences): MediaPrivacy = MediaPrivacy(
    Hidden.entries.filter { it.name in prefs.getStringSet("prism.hidden", emptySet()).orEmpty() }.toSet(),
    prefs.getStringSet("prism.excluded", emptySet()).orEmpty().toSet()
)

internal fun MediaPrivacy.save(prefs: SharedPreferences) {
    prefs.edit().putStringSet("prism.hidden", hidden.map { it.name }.toSet())
        .putStringSet("prism.excluded", excluded).apply()
}
