package com.equwal.sbm

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar

/** The list: search as you type, tap to open, press and hold for more. */
class MainActivity : Activity() {
    private lateinit var search: EditText
    private lateinit var empty: TextView
    private val rows = Rows()
    private var all: List<Bookmark> = emptyList()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setActionBar(toolbar)
        search = findViewById(R.id.search)
        empty = findViewById(R.id.empty)
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
            override fun afterTextChanged(s: Editable?) = show()
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
        load()
        if (Sync.signedIn(this)) sync(quiet = true)
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
        val actions = arrayOf(getString(R.string.open), getString(R.string.copy), getString(R.string.share))
        AlertDialog.Builder(this)
            .setTitle(b.desc.ifEmpty { b.url })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> open(b.url)
                    1 -> copy(b.url)
                    else -> share(b)
                }
            }
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
        const val KOFI = "https://ko-fi.com/truex"
    }
}
