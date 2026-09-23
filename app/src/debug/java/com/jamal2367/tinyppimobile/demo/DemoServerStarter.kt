package com.jamal2367.tinyppimobile.demo

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Starts [DemoServer] when a debug build starts, without the app's own code
 * having to know it exists: a provider is created before the application
 * object's first screen, and a release build has neither this class nor the
 * manifest entry that names it.
 */
class DemoServerStarter : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let(DemoServer::start)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
