package org.righteffort.ourgrocerylist.rules

import org.junit.rules.ExternalResource
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date


class TimberTestRule : ExternalResource() {
    private lateinit var tree: Timber.Tree

    override fun before() {
        tree = StdoutTree()
        Timber.plant(tree)
    }

    override fun after() {
        Timber.uproot(tree)
    }

    private class StdoutTree : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val level = when (priority) {
                android.util.Log.VERBOSE -> "V"
                android.util.Log.DEBUG   -> "D"
                android.util.Log.INFO    -> "I"
                android.util.Log.WARN    -> "W"
                android.util.Log.ERROR   -> "E"
                android.util.Log.ASSERT  -> "A"
                else                     -> priority.toString()
            }
            println(if (tag != null) "${SimpleDateFormat("HH:mm:ss.SSS").format(Date())} $level/$tag: $message" else "$level: $message")
        }
    }
}
