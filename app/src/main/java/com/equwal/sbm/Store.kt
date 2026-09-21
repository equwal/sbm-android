package com.equwal.sbm

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The bookmark file. It is the file that the user chose, or else, after a
 * sign-in to sync, a file of the app. The app keeps only the address of the
 * file and reads the file again each time, so a file that Syncthing or git
 * changes is always current.
 */
object Store {
    private const val PREFS = "sbm"
    private const val KEY = "file"

    /** The file that the user chose, or null. */
    fun uri(ctx: Context): Uri? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let(Uri::parse)

    /** The file to use: the chosen file, else the file of the app when sync is on. */
    fun file(ctx: Context): Uri? =
        uri(ctx) ?: if (Sync.signedIn(ctx)) Uri.fromFile(File(ctx.filesDir, "bookmarks")) else null

    fun choose(ctx: Context, uri: Uri) {
        ctx.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, uri.toString()).apply()
    }

    fun read(ctx: Context): String {
        val uri = file(ctx) ?: return ""
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        } catch (e: FileNotFoundException) {
            if (uri.scheme == "file") "" else throw e // the file of the app comes with the first sync
        }
    }

    /** Write the whole file. */
    fun write(ctx: Context, text: String) {
        val uri = file(ctx) ?: throw IOException("no bookmark file")
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("cannot write the bookmark file")
    }

    /**
     * Add [b] at the end of the file. When the file already has the same page,
     * add nothing and return that bookmark.
     */
    fun add(ctx: Context, b: Bookmark): Bookmark? {
        val uri = file(ctx) ?: throw IOException("no bookmark file")
        val text = read(ctx)
        Tsv.find(Tsv.parse(text), b.url)?.let { return it }
        val extra = Tsv.appendix(text, Tsv.format(b))
        val appended = try {
            ctx.contentResolver.openOutputStream(uri, "wa")?.use { it.write(extra.toByteArray(Charsets.UTF_8)) } != null
        } catch (e: Exception) {
            false
        }
        // Some document providers cannot append, and some write over the file
        // instead. Then write the whole file.
        if (!appended || read(ctx) != text + extra) write(ctx, text + extra)
        return null
    }
}
