package com.example.tracker

import androidx.compose.ui.graphics.Color

/**
 * Classifies music sessions into intuitive genre categories based on artist names,
 * track titles, album names, or metadata keywords.
 */
object GenreClassifier {

    // Distinctive, modern Bento palette colors for genres
    val PopColor = Color(0xFF8E24AA)          // Vibrant Violet
    val HipHopColor = Color(0xFFE65100)       // Deep Amber / Orange
    val ElectronicColor = Color(0xFF00838F)   // Cyan / Ocean Teal
    val RnBColor = Color(0xFFC2185B)          // Rose / Crimson
    val RockColor = Color(0xFF1565C0)         // Rich Cobalt Blue
    val LoFiColor = Color(0xFF2E7D32)         // Mint / Forest Green
    val ClassicalColor = Color(0xFFF9A825)    // Golden Amber
    val IndieColor = Color(0xFF6A1B9A)        // Deep Purple
    val JazzColor = Color(0xFF4E342E)         // Warm Mocha
    val CountryColor = Color(0xFFD84315)      // Rustic Rust
    val LatinColor = Color(0xFFC2185B)        // Warm Carmine
    val MetalColor = Color(0xFF37474F)        // Charcoal Slate
    val OtherColor = Color(0xFF546E7A)        // Slate Grey

    fun getColorForGenre(genre: String): Color {
        return when (genre.lowercase().trim()) {
            "pop" -> PopColor
            "hip-hop / rap", "hip-hop", "rap" -> HipHopColor
            "electronic", "edm", "dance" -> ElectronicColor
            "r&b / soul", "r&b", "soul" -> RnBColor
            "rock", "rock / alternative", "alternative" -> RockColor
            "metal", "heavy metal" -> MetalColor
            "lo-fi / chill", "lo-fi", "chill", "ambient" -> LoFiColor
            "classical / instrumental", "classical", "instrumental", "soundtrack", "score" -> ClassicalColor
            "indie / folk", "indie", "folk", "singer/songwriter" -> IndieColor
            "jazz / blues", "jazz", "blues" -> JazzColor
            "country", "country / americana" -> CountryColor
            "latin", "reggaeton" -> LatinColor
            else -> OtherColor
        }
    }

    /**
     * Normalizes a genre name returned by an external public music API (e.g. iTunes Search API)
     * into a canonical category for consistent analytics.
     */
    fun normalizeApiGenre(rawGenre: String): String {
        val trimmed = rawGenre.trim()
        val lower = trimmed.lowercase()

        return when {
            lower.contains("hip-hop") || lower.contains("rap") || lower.contains("trap") -> "Hip-Hop / Rap"
            lower.contains("r&b") || lower.contains("soul") -> "R&B / Soul"
            lower.contains("dance") || lower.contains("electronic") || lower.contains("edm") || lower.contains("house") || lower.contains("techno") -> "Electronic"
            lower.contains("metal") -> "Metal"
            lower.contains("hard rock") || lower.contains("classic rock") || lower.contains("rock") -> "Rock"
            lower.contains("alternative") || lower.contains("indie") || lower.contains("folk") || lower.contains("singer/songwriter") -> "Indie / Folk"
            lower.contains("soundtrack") || lower.contains("score") || lower.contains("classical") || lower.contains("orchestral") || lower.contains("instrumental") -> "Classical / Instrumental"
            lower.contains("jazz") || lower.contains("blues") -> "Jazz / Blues"
            lower.contains("lo-fi") || lower.contains("lofi") || lower.contains("chill") || lower.contains("ambient") -> "Lo-Fi / Chill"
            lower.contains("country") || lower.contains("americana") || lower.contains("bluegrass") -> "Country / Americana"
            lower.contains("latin") || lower.contains("reggaeton") || lower.contains("salsa") -> "Latin"
            lower.contains("k-pop") || lower.contains("j-pop") -> "Pop"
            lower.contains("pop") -> "Pop"
            trimmed.isNotBlank() -> trimmed.split("/").first().trim()
            else -> "Pop"
        }
    }

    fun classify(artist: String?, title: String?, album: String?): String {
        val combined = "${artist ?: ""} ${title ?: ""} ${album ?: ""}".lowercase()

        return when {
            containsAny(combined, "lofi", "chillhop", "chilledcow", "sleep", "study", "relaxing", "rain", "coffee", "ambient", "meditation") -> "Lo-Fi / Chill"
            containsAny(combined, "daft punk", "avicii", "calvin harris", "marshmello", "deadmau5", "skrillex", "edm", "electronic", "techno", "house", "remix", "tiesto", "david guetta", "alesso", "kygo", "chainsmokers", "zedd") -> "Electronic"
            containsAny(combined, "kendrick lamar", "drake", "eminem", "travis scott", "post malone", "kanye", "21 savage", "j. cole", "hip hop", "hip-hop", "rap", "future", "lil wayne", "cardi b", "nicki minaj", "metro boomin", "jack harlow") -> "Hip-Hop / Rap"
            containsAny(combined, "the weeknd", "sza", "bruno mars", "frank ocean", "beyoncé", "beyonce", "r&b", "soul", "alicia keys", "khalid", "daniel caesar", "usher", "rihanna", "chris brown", "steve lacy") -> "R&B / Soul"
            containsAny(combined, "coldplay", "imagine dragons", "queen", "linkin park", "arctic monkeys", "nirvana", "foo fighters", "the killers", "rock", "metal", "ac/dc", "led zeppelin", "radiohead", "green day", "red hot chili peppers") -> "Rock"
            containsAny(combined, "beethoven", "mozart", "chopin", "hans zimmer", "ludovico einaudi", "max richter", "orchestra", "classical", "symphony", "bach", "piano solo", "soundtrack") -> "Classical / Instrumental"
            containsAny(combined, "hozier", "bon iver", "lumineers", "phoebe bridgers", "vampire weekend", "vance joy", "indie", "folk", "fleet foxes", "boygenius", "lord huron") -> "Indie / Folk"
            containsAny(combined, "taylor swift", "dua lipa", "billie eilish", "olivia rodrigo", "ariana grande", "ed sheeran", "harry styles", "katy perry", "justin bieber", "pop", "sabrina carpenter", "chappell roan", "charli xcx", "miley cyrus", "lady gaga", "shawn mendes", "selena gomez") -> "Pop"
            containsAny(combined, "miles davis", "coltrane", "jazz", "blues", "bb king", "norah jones", "chet baker") -> "Jazz / Blues"
            else -> "Pop" // Default to Pop as YouTube Music's most common category
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }
}
