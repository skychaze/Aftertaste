package com.example.tracker

import java.text.Normalizer
import java.util.Locale

internal data class GenreTag(val name: String, val count: Int)
internal data class ScoredGenre(val genre: String, val confidence: Double)

internal object GenreTags {
    private val ignored = Regex("^(19|20)\\d{2}s?$|^(seen live|favorites?|favourites?|love(d)?|beautiful|sad|happy|chill|relaxing|male vocalists?|female vocalists?|american|british)$")
    private val styles = mapOf(
        "hip hop" to "Hip-Hop / Rap", "hip hop rap" to "Hip-Hop / Rap", "rap" to "Hip-Hop / Rap", "trap" to "Hip-Hop / Rap",
        "r&b" to "R&B / Soul", "r&b soul" to "R&B / Soul", "rhythm and blues" to "R&B / Soul", "soul" to "R&B / Soul", "neo soul" to "R&B / Soul",
        "house" to "Electronic", "techno" to "Electronic", "trance" to "Electronic", "edm" to "Electronic", "electronic" to "Electronic", "dance" to "Electronic", "dubstep" to "Electronic", "drum and bass" to "Electronic",
        "rock" to "Rock", "hard rock" to "Rock", "classic rock" to "Rock", "alternative rock" to "Rock", "punk rock" to "Rock",
        "metal" to "Metal", "heavy metal" to "Heavy Metal", "death metal" to "Metal", "black metal" to "Metal",
        "indie" to "Indie / Folk", "indie rock" to "Indie / Folk", "folk" to "Indie / Folk", "alternative" to "Indie / Folk", "singer songwriter" to "Indie / Folk",
        "lofi" to "Lo-Fi / Chill", "lo fi" to "Lo-Fi / Chill", "chillhop" to "Lo-Fi / Chill", "ambient" to "Lo-Fi / Chill", "downtempo" to "Lo-Fi / Chill",
        "classical" to "Classical / Instrumental", "orchestral" to "Classical / Instrumental", "instrumental" to "Classical / Instrumental", "soundtrack" to "Classical / Instrumental", "film score" to "Classical / Instrumental",
        "jazz" to "Jazz / Blues", "blues" to "Jazz / Blues", "bebop" to "Jazz / Blues",
        "country" to "Country / Americana", "americana" to "Country / Americana", "bluegrass" to "Country / Americana",
        "latin" to "Latin", "reggaeton" to "Latin", "salsa" to "Latin", "bachata" to "Latin",
        "pop" to "Pop", "dance pop" to "Pop", "synth pop" to "Pop", "k pop" to "Pop", "j pop" to "Pop", "c pop" to "Pop",
        "mandopop" to "Pop", "cantopop" to "Pop", "romantic" to "Romantic", "romance" to "Romantic", "romantic songs" to "Romantic", "love song" to "Romantic", "love songs" to "Romantic",
        "bollywood" to "Bollywood"
    )
    private val languages = buildMap {
        Locale.getISOLanguages().forEach { code ->
            val language = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
            val tag = normalizedTag(language)
            if (tag.isNotBlank() && tag !in styles) put(tag, language)
        }
        putAll(mapOf(
            "bangla" to "Bengali", "bengali" to "Bengali", "k pop" to "Korean",
            "mandarin" to "Chinese", "cantonese" to "Chinese", "c pop" to "Chinese",
            "mandopop" to "Chinese", "cantopop" to "Chinese", "bollywood" to "Hindi",
            "j pop" to "Japanese"
        ))
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("[‐‑‒–—_]"), " ")
        .replace(Regex("\\s+"), " ")

    fun trackKey(artist: String?, title: String?): String? {
        val a = normalize(artist.orEmpty())
        val t = normalize(title.orEmpty())
        if (a.isBlank() || t.isBlank() || a == "youtube music" || t == "unknown track") return null
        return a + "\u001f" + t
    }

    private fun normalizedTag(raw: String): String = normalize(raw)
        .replace(Regex("[^\\p{L}\\p{N}& ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun meaning(raw: String): Pair<String?, String?> {
        val tag = normalizedTag(raw)
        if (ignored.matches(tag)) return null to null
        val languageEntry = languages.entries.firstOrNull { tag == it.key || tag.startsWith(it.key + " ") }
        val language = languageEntry?.value
        val style = styles[tag] ?: languageEntry?.let { styles[tag.removePrefix(it.key).trim()] }
        return language to style
    }

    private fun label(language: String?, style: String?): String? = when {
        style == "Bollywood" && (language == null || language == "Hindi") -> "Bollywood"
        language == "Korean" && style == "Pop" -> "K-Pop"
        language == "Chinese" && style == "Pop" -> "C-Pop"
        language == "Japanese" && style == "Pop" -> "J-Pop"
        language == null || language == "English" -> style
        style == null -> language
        else -> language + "-" + style
    }

    private fun titleLanguage(title: String?): String? = when {
        title == null -> null
        title.any { it in '\uAC00'..'\uD7AF' } -> "Korean"
        title.any { it in '\u0980'..'\u09FF' } -> "Bengali"
        title.any { it in '\u3040'..'\u30FF' } -> "Japanese"
        else -> null
    }

    fun withTitleLanguage(genre: String, title: String): String {
        val language = titleLanguage(title) ?: return genre
        if (genre == "Bollywood" || genre == "K-Pop" || genre == "C-Pop" || genre == "J-Pop" ||
            languages.values.any { genre == it || genre.startsWith(it + "-") }
        ) return genre
        return label(language, genre.takeUnless { it == "Other" }) ?: genre
    }

    fun mapTag(raw: String): String? {
        val (language, style) = meaning(raw)
        return label(language, style)
    }

    fun score(tags: List<GenreTag>, sourceWeight: Double, title: String? = null): ScoredGenre? {
        val languageTotals = mutableMapOf<String, Double>()
        val styleTotals = mutableMapOf<String, Double>()
        tags.take(30).forEach { tag ->
            if (tag.count <= 0) return@forEach
            val (language, style) = meaning(tag.name)
            val weight = tag.count.coerceAtMost(1000).toDouble()
            if (language != null) languageTotals[language] = languageTotals.getOrDefault(language, 0.0) + weight
            if (style != null) styleTotals[style] = styleTotals.getOrDefault(style, 0.0) + weight
        }
        if (languageTotals.isEmpty() && styleTotals.isEmpty()) return null
        val taggedLanguage = languageTotals.maxByOrNull { it.value }?.takeIf { winner ->
            languageTotals.values.count { it == winner.value } == 1
        }?.key
        val language = titleLanguage(title) ?: taggedLanguage
        val style = styleTotals.maxByOrNull { it.value }?.key
        val genre = label(language, style) ?: return null
        val winningTag = style ?: language ?: return null
        val totals = styleTotals.ifEmpty { languageTotals }
        val share = (totals[winningTag] ?: 0.0) / totals.values.sum()
        return ScoredGenre(genre, (sourceWeight * (0.55 + 0.45 * share)).coerceIn(0.0, 1.0))
    }
}
