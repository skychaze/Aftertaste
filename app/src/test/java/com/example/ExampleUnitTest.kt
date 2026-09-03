package com.example

import com.example.tracker.GenreClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun genreClassifier_accuratelyDetectsPopularGenres() {
        assertEquals("Electronic", GenreClassifier.classify("Daft Punk", "Get Lucky", "Random Access Memories"))
        assertEquals("Electronic", GenreClassifier.classify("Avicii", "Levels", null))
        assertEquals("Hip-Hop / Rap", GenreClassifier.classify("Kendrick Lamar", "HUMBLE.", "DAMN."))
        assertEquals("Hip-Hop / Rap", GenreClassifier.classify("Travis Scott", "SICKO MODE", "ASTROWORLD"))
        assertEquals("R&B / Soul", GenreClassifier.classify("The Weeknd", "Blinding Lights", "After Hours"))
        assertEquals("R&B / Soul", GenreClassifier.classify("SZA", "Kill Bill", "SOS"))
        assertEquals("Rock", GenreClassifier.classify("Coldplay", "Viva La Vida", null))
        assertEquals("Rock", GenreClassifier.classify("Arctic Monkeys", "Do I Wanna Know?", "AM"))
        assertEquals("Lo-Fi / Chill", GenreClassifier.classify("Lofi Girl", "Rainy Night Study Beats", "Chillhop"))
        assertEquals("Classical / Instrumental", GenreClassifier.classify("Hans Zimmer", "Time", "Inception"))
        assertEquals("Indie / Folk", GenreClassifier.classify("Hozier", "Too Sweet", "Unreal Unearth"))
        assertEquals("Pop", GenreClassifier.classify("Taylor Swift", "Anti-Hero", "Midnights"))
    }

    @Test
    fun genreClassifier_providesDistinctColors() {
        val popColor = GenreClassifier.getColorForGenre("Pop")
        val rockColor = GenreClassifier.getColorForGenre("Rock")
        val edmColor = GenreClassifier.getColorForGenre("Electronic")

        assertNotNull(popColor)
        assertNotNull(rockColor)
        assertNotNull(edmColor)
        assertTrue(popColor != rockColor)
        assertTrue(rockColor != edmColor)
    }

    @Test
    fun genreClassifier_normalizesExternalApiGenres() {
        assertEquals("Hip-Hop / Rap", GenreClassifier.normalizeApiGenre("Hip-Hop/Rap"))
        assertEquals("R&B / Soul", GenreClassifier.normalizeApiGenre("R&B/Soul"))
        assertEquals("Electronic", GenreClassifier.normalizeApiGenre("Dance"))
        assertEquals("Electronic", GenreClassifier.normalizeApiGenre("Electronic"))
        assertEquals("Rock", GenreClassifier.normalizeApiGenre("Hard Rock"))
        assertEquals("Indie / Folk", GenreClassifier.normalizeApiGenre("Alternative"))
        assertEquals("Classical / Instrumental", GenreClassifier.normalizeApiGenre("Soundtrack"))
        assertEquals("Country / Americana", GenreClassifier.normalizeApiGenre("Country"))
        assertEquals("Latin", GenreClassifier.normalizeApiGenre("Latin Urban"))
    }
}
