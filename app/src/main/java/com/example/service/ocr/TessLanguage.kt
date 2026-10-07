package com.amresalehin.emreshots.service.ocr

data class TessLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val script: String = "Latin",
    val isBundled: Boolean = false
) {
    val downloadUrl: String
        get() = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$code.traineddata"

    val displayName: String
        get() = if (englishName == nativeName) englishName else "$englishName ($nativeName)"

    companion object {
        val ALL_LANGUAGES: List<TessLanguage> = listOf(
            TessLanguage("eng", "English", "English", "Latin", isBundled = true),
            TessLanguage("spa", "Spanish", "Español", "Latin"),
            TessLanguage("fra", "French", "Français", "Latin"),
            TessLanguage("deu", "German", "Deutsch", "Latin"),
            TessLanguage("tur", "Turkish", "Türkçe", "Latin"),
            TessLanguage("ita", "Italian", "Italiano", "Latin"),
            TessLanguage("por", "Portuguese", "Português", "Latin"),
            TessLanguage("rus", "Russian", "Русский", "Cyrillic"),
            TessLanguage("ukr", "Ukrainian", "Українська", "Cyrillic"),
            TessLanguage("pol", "Polish", "Polski", "Latin"),
            TessLanguage("nld", "Dutch", "Nederlands", "Latin"),
            TessLanguage("chi_sim", "Chinese (Simplified)", "简体中文", "Han (Simplified)"),
            TessLanguage("chi_tra", "Chinese (Traditional)", "繁體中文", "Han (Traditional)"),
            TessLanguage("jpn", "Japanese", "日本語", "Japanese"),
            TessLanguage("kor", "Korean", "한국어", "Hangul"),
            TessLanguage("ara", "Arabic", "العربية", "Arabic"),
            TessLanguage("hin", "Hindi", "हिन्दी", "Devanagari"),
            TessLanguage("ben", "Bengali", "বাংলা", "Bengali"),
            TessLanguage("vie", "Vietnamese", "Tiếng Việt", "Latin"),
            TessLanguage("ind", "Indonesian", "Bahasa Indonesia", "Latin"),
            TessLanguage("swe", "Swedish", "Svenska", "Latin"),
            TessLanguage("nor", "Norwegian", "Norsk", "Latin"),
            TessLanguage("dan", "Danish", "Dansk", "Latin"),
            TessLanguage("fin", "Finnish", "Suomi", "Latin"),
            TessLanguage("ces", "Czech", "Čeština", "Latin"),
            TessLanguage("ell", "Greek", "Ελληνικά", "Greek"),
            TessLanguage("heb", "Hebrew", "עברית", "Hebrew"),
            TessLanguage("tha", "Thai", "ไทย", "Thai")
        )

        fun findByCode(code: String): TessLanguage {
            return ALL_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }
                ?: TessLanguage(code = code, englishName = code.uppercase(), nativeName = code)
        }
    }
}
