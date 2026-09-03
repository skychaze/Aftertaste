package com.example.tracker

import java.net.URLEncoder

internal object ITunesSearchApi {
    fun buildSearchUrl(artist: String?, title: String?): String {
        val query = "${artist?.trim().orEmpty()} ${title?.trim().orEmpty()}".trim()
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://itunes.apple.com/search?term=$encoded&entity=song&limit=1"
    }
}
