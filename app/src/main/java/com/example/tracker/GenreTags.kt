package com.example.tracker

import java.text.Normalizer
import java.util.Locale

internal data class GenreTag(val name: String, val count: Int)
internal data class ScoredGenre(val genre: String, val confidence: Double)

internal object GenreTags {
    private val ignored = Regex("^(19|20)\\d{2}s?$|^(seen live|favorites?|favourites?|love(d)?|beautiful|sad|happy|chill|relaxing|male vocalists?|female vocalists?|american|british)$")
    private val aliases = mapOf(
        "hip hop" to "Hip-Hop / Rap", "hip hop rap" to "Hip-Hop / Rap", "rap" to "Hip-Hop / Rap", "trap" to "Hip-Hop / Rap",
        "r&b" to "R&B / Soul", "r&b soul" to "R&B / Soul", "rhythm and blues" to "R&B / Soul", "soul" to "R&B / Soul", "neo soul" to "R&B / Soul",
        "house" to "Electronic", "techno" to "Electronic", "trance" to "Electronic", "edm" to "Electronic", "electronic" to "Electronic", "dance" to "Electronic", "dubstep" to "Electronic", "drum and bass" to "Electronic",
        "rock" to "Rock", "hard rock" to "Rock", "classic rock" to "Rock", "alternative rock" to "Rock", "punk rock" to "Rock",
        "metal" to "Metal", "heavy metal" to "Metal", "death metal" to "Metal", "black metal" to "Metal",
        "indie" to "Indie / Folk", "indie rock" to "Indie / Folk", "folk" to "Indie / Folk", "alternative" to "Indie / Folk", "singer songwriter" to "Indie / Folk",
        "lofi" to "Lo-Fi / Chill", "lo fi" to "Lo-Fi / Chill", "chillhop" to "Lo-Fi / Chill", "ambient" to "Lo-Fi / Chill", "downtempo" to "Lo-Fi / Chill",
        "classical" to "Classical / Instrumental", "orchestral" to "Classical / Instrumental", "instrumental" to "Classical / Instrumental", "soundtrack" to "Classical / Instrumental", "film score" to "Classical / Instrumental",
        "jazz" to "Jazz / Blues", "blues" to "Jazz / Blues", "bebop" to "Jazz / Blues",
        "country" to "Country / Americana", "americana" to "Country / Americana", "bluegrass" to "Country / Americana",
        "latin" to "Latin", "reggaeton" to "Latin", "salsa" to "Latin", "bachata" to "Latin",
        "pop" to "Pop", "dance pop" to "Pop", "synth pop" to "Pop", "k pop" to "Pop", "j pop" to "Pop"
    )

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("[‐‑‒–—_]"), " ")
        .replace(Regex("\\s+"), " ")

    fun trackKey(artist: String?, title: String?): String? {
        val a = normalize(artist.orEmpty())
        val t = normalize(title.orEmpty())
        if (a.isBlank() || t.isBlank() || a == "youtube music" || t == "unknown track") return null
        return "$a\u001f$t"
    }

    fun mapTag(raw: String): String? {
        val tag = normalize(raw).replace(Regex("[^\\p{L}\\p{N}& ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (ignored.matches(tag)) return null
        return aliases[tag]
    }

    fun score(tags: List<GenreTag>, sourceWeight: Double): ScoredGenre? {
        val totals = mutableMapOf<String, Double>()
        tags.take(30).forEach { tag ->
            val genre = mapTag(tag.name) ?: return@forEach
            if (tag.count > 0) totals[genre] = totals.getOrDefault(genre, 0.0) + tag.count.coerceAtMost(1000).toDouble()
        }
        val winner = totals.maxByOrNull { it.value } ?: return null
        val share = winner.value / totals.values.sum()
        return ScoredGenre(winner.key, (sourceWeight * (0.55 + 0.45 * share)).coerceIn(0.0, 1.0))
    }
}
