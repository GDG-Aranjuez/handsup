package es.gdgaranjuez.handsup.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Lang(val code: String) {
    ES("es"),
    EN("en");

    companion object {
        fun fromSystem(languageTag: String): Lang = if (languageTag.lowercase().startsWith("en")) EN else ES
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code }
    }
}

/** UI language. Reading [lang] from a composable makes it recompose when the language changes. */
object I18n {
    var lang by mutableStateOf(Lang.ES)

    fun tr(key: String, params: Map<String, Any> = emptyMap(), lang: Lang = this.lang): String {
        val table = if (lang == Lang.EN) GENERATED_EN else GENERATED_ES
        val raw = table[key] ?: GENERATED_ES[key] ?: key
        return PLACEHOLDER.replace(raw) { params[it.groupValues[1]]?.toString().orEmpty() }
    }

    private val PLACEHOLDER = Regex("\\{(\\w+)\\}")
}

/** Translate [key]; `{name}` placeholders come from [params]. Falls back to Spanish, then to the key. */
fun t(key: String, vararg params: Pair<String, Any>): String = I18n.tr(key, params.toMap())
