// SPDX-License-Identifier: Apache-2.0

package app.baldphone.neo.launcher.apps.data

import android.content.Context
import android.content.SharedPreferences

import androidx.core.content.edit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-defined app names, keyed by flattened component name ("package/class").
 *
 * Overrides are applied in [AppsRepository.allAppsFlow], so a renamed app shows its new
 * name everywhere (Apps screen, pinned home pages, customised home buttons). The original
 * label in the database is never modified, so removing the override restores it.
 */
object AppLabels {
    private const val PREFS_NAME = "custom_app_labels"

    private lateinit var prefs: SharedPreferences
    private val _labels = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Current overrides: component name -> custom label. */
    val labels: StateFlow<Map<String, String>> = _labels.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _labels.value = readAll()
    }

    fun get(componentName: String): String? = _labels.value[componentName]

    fun isCustomized(componentName: String): Boolean = _labels.value.containsKey(componentName)

    /**
     * Sets the custom label for [componentName]. A null or blank [label] removes the override.
     */
    fun set(componentName: String, label: String?) {
        prefs.edit {
            if (label.isNullOrBlank()) remove(componentName) else putString(componentName, label.trim())
        }
        _labels.value = readAll()
    }

    private fun readAll(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
}
