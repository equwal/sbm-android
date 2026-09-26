package com.equwal.sbm

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar

/** The other sites and apps of the same author, from the menu of the list. A tap opens the page in a browser. */
class MoreAppsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_more_apps)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setActionBar(toolbar)

        // The same as the list screen: the toolbar colour goes under the
        // status bar, and the rest of the screen stays clear of the bars.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                toolbar.setPadding(0, bars.top, 0, 0)
                v.setPadding(bars.left, 0, bars.right, bars.bottom)
                insets
            }
        }

        val list = findViewById<ListView>(R.id.list)
        list.adapter = Rows()
        list.setOnItemClickListener { _, _, position, _ -> open(MORE_APPS[position].url) }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_app, url), Toast.LENGTH_LONG).show()
        }
    }

    private inner class Rows : BaseAdapter() {
        override fun getCount() = MORE_APPS.size
        override fun getItem(position: Int) = MORE_APPS[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_more_app, parent, false)
            view.findViewById<TextView>(R.id.title).setText(MORE_APPS[position].name)
            view.findViewById<TextView>(R.id.detail).setText(MORE_APPS[position].line)
            return view
        }
    }
}
