package com.equwal.sbm

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Sync with an sbm-sync server (https://github.com/equwal/sbm-sync). The app
 * sends the file and the name of the version that the server gave last time.
 * The server merges and sends back the file, which the app then writes.
 */
object Sync {
    const val DEFAULT_SERVER = "https://sbmsync.com"
    private const val PREFS = "sync"
    private val worker by lazy { Executors.newSingleThreadExecutor() }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /**
     * A sync that did not work, with a message for the user. [urgent] means
     * that the user must act: sign in again, or see the account.
     */
    class Failure(message: String, val signIn: Boolean = false, val urgent: Boolean = signIn) : IOException(message)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun signedIn(ctx: Context) = prefs(ctx).getString("token", null) != null
    fun server(ctx: Context): String = prefs(ctx).getString("server", null) ?: DEFAULT_SERVER
    fun email(ctx: Context): String = prefs(ctx).getString("email", null).orEmpty()

    /** Sign in, keep the token, then sync. [done] gets the result on the main thread. */
    fun signIn(ctx: Context, server: String, email: String, password: String, done: (Result<Int>) -> Unit) {
        val app = ctx.applicationContext
        background(done) {
            val site = address(server)
            val form = "email=" + URLEncoder.encode(email.trim(), "UTF-8") +
                "&password=" + URLEncoder.encode(password, "UTF-8")
            val r = post(app, "$site/api/login", form.toByteArray(), "application/x-www-form-urlencoded", null)
            when (r.code) {
                200 -> {}
                401 -> throw Failure(app.getString(R.string.wrong_login))
                else -> throw Failure(r.text.trim())
            }
            prefs(app).edit().clear()
                .putString("server", site).putString("email", email.trim()).putString("token", r.text.trim())
                .apply()
            syncNow(app)
            count(app)
        }
    }

    fun signOut(ctx: Context) {
        val app = ctx.applicationContext
        val token = prefs(app).getString("token", null) ?: return
        val site = server(app)
        prefs(app).edit().remove("token").remove("version").remove("file").apply()
        worker.execute {
            try {
                post(app, "$site/api/logout", ByteArray(0), "text/plain", token)
            } catch (e: IOException) {
                // The token stays valid on the server; nothing else is lost.
            }
        }
    }

    /** Sync now. [done] gets, on the main thread, whether the file changed. */
    fun run(ctx: Context, done: (Result<Boolean>) -> Unit) {
        val app = ctx.applicationContext
        background(done) { syncNow(app) }
    }

    private fun count(ctx: Context) = Tsv.parse(Store.read(ctx)).size

    private fun syncNow(ctx: Context): Boolean {
        val p = prefs(ctx)
        val token = p.getString("token", null) ?: throw Failure(ctx.getString(R.string.sign_in_again), true)
        val file = Store.file(ctx)?.toString() ?: throw Failure(ctx.getString(R.string.choose_first))
        repeat(3) {
            // The version belongs to the file that it came from.
            val base = if (p.getString("file", null) == file) p.getString("version", null).orEmpty() else ""
            val sent = Store.read(ctx)
            val r = post(ctx, server(ctx) + "/api/sync?base=" + base, sent.toByteArray(Charsets.UTF_8),
                "text/plain; charset=utf-8", token)
            when (r.code) {
                200 -> {}
                401 -> {
                    p.edit().remove("token").apply()
                    throw Failure(ctx.getString(R.string.sign_in_again), true)
                }
                402 -> throw Failure(ctx.getString(R.string.sync_paused), urgent = true)
                else -> throw Failure(r.text.trim())
            }
            val version = r.version ?: throw Failure(ctx.getString(R.string.no_version))
            // A bookmark came in while the request ran: send the file again.
            if (Store.read(ctx) != sent) return@repeat
            val changed = r.text != sent
            if (changed) Store.write(ctx, r.text)
            p.edit().putString("file", file).putString("version", version).apply()
            return changed
        }
        throw Failure(ctx.getString(R.string.file_busy))
    }

    /** The address of a server as the user typed it, with https:// when it has no scheme. */
    fun address(typed: String): String {
        val t = typed.trim().trimEnd('/')
        return if ("://" in t) t else "https://$t"
    }

    private class Reply(val code: Int, val text: String, val version: String?)

    private fun post(ctx: Context, url: String, body: ByteArray, type: String, token: String?): Reply {
        val c = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw Failure(ctx.getString(R.string.bad_server, url))
        }
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return Reply(code, text, c.getHeaderField("Sbm-Version"))
        } catch (e: Failure) {
            throw e
        } catch (e: IOException) {
            throw Failure(ctx.getString(R.string.no_server, URL(url).host))
        } finally {
            c.disconnect()
        }
    }

    private fun <T> background(done: (Result<T>) -> Unit, work: () -> T) {
        worker.execute {
            val result = try {
                Result.success(work())
            } catch (e: Exception) {
                Result.failure(e)
            }
            main.post { done(result) }
        }
    }
}
