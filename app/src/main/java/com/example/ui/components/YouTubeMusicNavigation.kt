package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.tracker.YouTubeHelper

internal fun openTrackInYouTubeMusic(context: Context, title: String, artist: String) {
    val uri =
        Uri.Builder()
            .scheme("https")
            .authority("music.youtube.com")
            .path("/search")
            .appendQueryParameter("q", "$title $artist")
            .build()
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent(intent).setPackage(YouTubeHelper.PACKAGE_YOUTUBE_MUSIC))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                    context,
                    "Install YouTube Music or a browser to open this song.",
                    Toast.LENGTH_LONG,
                )
                .show()
        }
    }
}
