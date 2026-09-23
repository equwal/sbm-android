package com.equwal.sbm

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar

/**
 * The list: search as you type, tap to open, press and hold for more. During
 * a search, the page of the first match shows live above the list.
 */
class MainActivity : Activity() {
    private lateinit var search: EditText
    private lateinit var empty: TextView
    private lateinit var preview: LinearLayout
    private lateinit var caption: TextView
    private val rows = Rows()
    private var all: List<Bookmark> = emptyList()
    private var page: WebView? = null // made when the preview first shows
    private var previewed: Bookmark? = null // the bookmark in the preview
    private var picked: Bookmark? = null // the bookmark that the user picked with Preview
    private var framed: String? = null // the address in the web view
    private val later = Handler(Looper.getMainLooper())

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setActionBar(toolbar)
        search = findViewById(R.id.search)
        empty = findViewById(R.id.empty)
        preview = findViewById(R.id.preview)
        caption = findViewById(R.id.caption)
        caption.setOnClickListener { previewed?.let { open(it.url) } }
        val list = findViewById<ListView>(R.id.list)
        list.adapter = rows
        list.emptyView = empty

        // Android 15 and later draw the app under the system bars and the
        // keyboard. The toolbar colour goes under the status bar, and the
        // rest of the screen stays clear of the bars and the keyboard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                toolbar.setPadding(0, bars.top, 0, 0)
                v.setPadding(bars.left, 0, bars.right, bars.bottom)
                insets
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                picked = null
                show()
            }
        })
        search.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) {
                go()
                true
            } else {
                false
            }
        }
        list.setOnItemClickListener { _, _, position, _ -> open(rows.items[position].url) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            more(rows.items[position])
            true
        }
    }

    override fun onResume() {
        super.onResume()
        page?.onResume()
        load()
        if (Sync.signedIn(this)) sync(quiet = true)
    }

    override fun onPause() {
        page?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        later.removeCallbacksAndMessages(null)
        page?.destroy()
        super.onDestroy()
    }

    /** Sync, then show the file again. A quiet sync reports only what the user must act on. */
    private fun sync(quiet: Boolean) {
        Sync.run(this) { result ->
            if (isDestroyed) return@run
            result.onSuccess { changed ->
                if (changed) load()
                if (!quiet) toast(resources.getQuantityString(R.plurals.synced, all.size, all.size))
            }.onFailure { e ->
                if (!quiet || (e is Sync.Failure && e.urgent)) toast(getString(R.string.sync_failed, e.message))
                if (e is Sync.Failure && e.signIn) invalidateOptionsMenu()
            }
        }
    }

    private fun showSignIn() {
        val form = layoutInflater.inflate(R.layout.dialog_sign_in, null)
        val server = form.findViewById<EditText>(R.id.server)
        val email = form.findViewById<EditText>(R.id.email)
        val password = form.findViewById<EditText>(R.id.password)
        server.setText(Sync.server(this))
        email.setText(Sync.email(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.sign_in)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sign_in_button) { _, _ ->
                toast(getString(R.string.signing_in))
                Sync.signIn(this, server.text.toString(), email.text.toString(), password.text.toString()) { result ->
                    if (isDestroyed) return@signIn
                    result.onSuccess { n ->
                        toast(resources.getQuantityString(R.plurals.signed_in, n, Sync.email(this), n))
                        invalidateOptionsMenu()
                        load()
                    }.onFailure { e -> toast(getString(R.string.sync_failed, e.message)) }
                }
            }
            .show()
    }

    private fun load() {
        all = try {
            // Newest first: bm adds bookmarks at the end of the file.
            Tsv.parse(Store.read(this)).asReversed()
        } catch (e: Exception) {
            toast(getString(R.string.cannot_read, e.message))
            emptyList()
        }
        empty.setText(if (Store.file(this) == null) R.string.no_file else R.string.no_match)
        show()
    }

    private fun show() {
        rows.items = Fuzzy.filter(all, search.text.toString()) { "${it.desc} ${it.tags.joinToString(" ")} ${it.url}" }
        rows.notifyDataSetChanged()
        showPage(picked ?: if (search.text.isBlank()) null else rows.items.firstOrNull())
    }

    private val settings get() = getSharedPreferences("settings", MODE_PRIVATE)
    private val previewOn get() = settings.getBoolean("preview", true)

    /**
     * Show the page of [b] in the preview, or close the preview for null. The
     * page loads only when [b] stays in the preview for a moment, so that
     * fast typing does not load a page for each letter.
     */
    private fun showPage(b: Bookmark?) {
        later.removeCallbacksAndMessages(null)
        previewed = if (previewOn) b else null
        val shown = previewed
        if (shown == null) {
            preview.visibility = View.GONE
            loadPage(null)
            return
        }
        preview.visibility = View.VISIBLE
        val to = Preview.address(shown.url)
        caption.text = to ?: getString(R.string.no_preview, shown.url)
        if (to == null) loadPage(null) else later.postDelayed({ loadPage(to) }, PREVIEW_WAIT)
    }

    /** Load the page at [to] in the preview, or an empty page for null. */
    private fun loadPage(to: String?) {
        if (to == framed) return
        framed = to
        val view = page ?: if (to == null) return else newPage()
        view.loadUrl(to ?: "about:blank")
    }

    /**
     * A web view for the preview, under the caption. The page in it is only
     * to look at: it runs its scripts, as in a browser, but it cannot take
     * the focus from the search, open other apps or read the files of the
     * app. Its links stay in the preview, and an http link loads with https.
     */
    @SuppressLint("SetJavaScriptEnabled") // Most pages need scripts to show, and the page gets no interface to the app.
    private fun newPage(): WebView {
        val view = WebView(this)
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val to = Preview.address(url) ?: return true
                if (to == url) return false
                v.loadUrl(to)
                return true
            }

            // A page that stops the renderer must not stop the app: a new
            // web view takes the place of the old one.
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                preview.removeView(v)
                v.destroy()
                if (page === v) page = null
                framed = null
                return true
            }
        }
        preview.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page = view
        return view
    }

    /** Go opens the first bookmark. Without one, the text is an address or a web search, as in bm. */
    private fun go() {
        val first = rows.items.firstOrNull()
        when {
            first != null -> open(first.url)
            search.text.isNotBlank() -> open(Tsv.target(search.text.toString()))
        }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_app, url))
        }
    }

    private fun more(b: Bookmark) {
        val actions = listOfNotNull(
            getString(R.string.open) to { open(b.url) },
            if (previewOn) {
                getString(R.string.preview) to {
                    picked = b
                    show()
                }
            } else {
                null
            },
            getString(R.string.copy) to { copy(b.url) },
            getString(R.string.share) to { share(b) },
        )
        AlertDialog.Builder(this)
            .setTitle(b.desc.ifEmpty { b.url })
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun copy(url: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("URL", url))
        // Android 13 and later show their own message.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(getString(R.string.copied))
    }

    private fun share(b: Bookmark) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, b.url)
            .putExtra(Intent.EXTRA_SUBJECT, b.desc)
        startActivity(Intent.createChooser(send, null))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val on = Sync.signedIn(this)
        menu.findItem(R.id.sync_now).isVisible = on
        menu.findItem(R.id.sign_out).isVisible = on
        menu.findItem(R.id.sign_in).isVisible = !on
        menu.findItem(R.id.preview_on).isChecked = previewOn
        // Google Play does not allow links to payments outside Google Play.
        menu.findItem(R.id.donate).isVisible = BuildConfig.DONATE
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.add -> {
            startActivity(Intent(this, AddActivity::class.java))
            true
        }
        R.id.sync_now -> {
            sync(quiet = false)
            true
        }
        R.id.sign_in -> {
            showSignIn()
            true
        }
        R.id.sign_out -> {
            Sync.signOut(this)
            invalidateOptionsMenu()
            load()
            true
        }
        R.id.preview_on -> {
            settings.edit().putBoolean("preview", !previewOn).apply()
            invalidateOptionsMenu()
            show()
            true
        }
        R.id.donate -> {
            open(KOFI)
            true
        }
        R.id.open_file -> {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            startActivityForResult(pick, CHOOSE)
            true
        }
        R.id.new_file -> {
            val make = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, "bookmarks")
            startActivityForResult(make, CHOOSE)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (requestCode == CHOOSE && resultCode == RESULT_OK && uri != null) {
            Store.choose(this, uri)
            load()
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private inner class Rows : BaseAdapter() {
        var items: List<Bookmark> = emptyList()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item, parent, false)
            val b = items[position]
            view.findViewById<TextView>(R.id.title).text = b.desc.ifEmpty { b.url }
            view.findViewById<TextView>(R.id.detail).text =
                listOf(Tsv.host(b.url), b.tags.joinToString(" ")).filter { it.isNotEmpty() }.joinToString("  ·  ")
            return view
        }
    }

    private companion object {
        const val CHOOSE = 1
        const val PREVIEW_WAIT = 400L // ms
        const val KOFI = "https://ko-fi.com/truex"
    }
}
