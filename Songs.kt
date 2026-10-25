package com.example.musicplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Song(val id: Long, val title: String, val artist: String,
                val album: String, val durationMs: Long, val uri: Uri)

/** MediaStore দিয়ে ফোনের সব গান একবারে scan */
fun scanSongs(ctx: Context): List<Song> {
    val out = mutableListOf<Song>()
    val c = ctx.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION),
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000",
        null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC") ?: return out
    c.use {
        while (it.moveToNext()) {
            val id = it.getLong(0)
            out += Song(id, it.getString(1) ?: "Unknown", it.getString(2)?.takeIf { a -> a != "<unknown>" } ?: "Unknown Artist",
                it.getString(3) ?: "", it.getLong(4),
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
        }
    }
    return out
}

fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
