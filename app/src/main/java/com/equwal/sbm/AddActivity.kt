package com.equwal.sbm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/** Add a bookmark: from "Share" in a browser, or from the menu of the list. */
class AddActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (Store.file(this) == null) {
            toast(getString(R.string.choose_first))
            finish()
            return
        }
        setContentView(R.layout.activity_add)
        val url = findViewById<EditText>(R.id.url)
        val desc = findViewById<EditText>(R.id.desc)
        val tags = findViewById<EditText>(R.id.tags)

        if (state == null && intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            url.setText(Tsv.firstUrl(text) ?: text.trim())
            desc.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
        }

        val known = try {
            Tsv.parse(Store.read(this)).flatMap { it.tags }.distinct().sorted()
        } catch (e: Exception) {
            emptyList()
        }
        val hint = findViewById<TextView>(R.id.known)
        if (known.isEmpty()) {
            hint.visibility = View.GONE
        } else {
            hint.text = getString(R.string.known_tags, known.joinToString(" "))
        }

        findViewById<Button>(R.id.cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.save).setOnClickListener {
            val b = Bookmark(
                url.text.toString().filterNot { it.isWhitespace() },
                Tsv.clean(desc.text.toString()).trim(),
                tags.text.toString().split(' ', '\t', '\n').filter { it.isNotEmpty() })
            if (b.url.isEmpty()) {
                url.error = getString(R.string.need_url)
                return@setOnClickListener
            }
            try {
                val existing = Store.add(this, b)
                toast(if (existing == null) getString(R.string.added) else getString(R.string.already, existing.desc.ifEmpty { existing.url }))
                // The list syncs when it opens; this sends the new bookmark now.
                if (existing == null && Sync.signedIn(this)) Sync.run(this) {}
                finish()
            } catch (e: Exception) {
                toast(getString(R.string.cannot_write, e.message))
            }
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
