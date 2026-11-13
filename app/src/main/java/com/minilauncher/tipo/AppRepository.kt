package com.minilauncher.tipo

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

class AppRepository(
    private val pm: PackageManager,
    private val selfPackage: String
) {

    class Entry(
        @JvmField val label: String,
        @JvmField val norm: String,
        @JvmField val pkg: String,
        @JvmField val cls: String
    )

    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    private var apps: Array<Entry> = emptyArray()
    private var hits = IntArray(0)
    private var tiers = ByteArray(0)

    var count = 0
        private set

    fun load(done: () -> Unit) {
        executor.execute {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
            val out = ArrayList<Entry>(list.size)
            for (ri in list) {
                val ai = ri.activityInfo
                if (ai.packageName == selfPackage) continue
                val label = ri.loadLabel(pm).toString()
                out.add(Entry(label, normalize(label), ai.packageName, ai.name))
            }
            out.sortBy { it.norm }
            val arr = out.toTypedArray()
            main.post {
                apps = arr
                done()
            }
        }
    }

    val size: Int
        get() = apps.size

    fun entryAt(position: Int): Entry = apps[hits[position]]

    fun find(pkg: String, cls: String): Entry? {
        val a = apps
        for (i in a.indices) {
            val e = a[i]
            if (e.pkg == pkg && e.cls == cls) return e
        }
        return null
    }

    fun filter(q: String) {
        val a = apps
        if (hits.size < a.size) {
            hits = IntArray(a.size)
            tiers = ByteArray(a.size)
        }
        var n = 0
        if (q.isEmpty()) {
            while (n < a.size) {
                hits[n] = n
                n++
            }
            count = n
            return
        }
        var any = false
        for (i in a.indices) {
            val t = tierOf(a[i].norm, q)
            tiers[i] = t.toByte()
            if (t < 4) any = true
        }
        if (!any && q.length >= 2) {
            for (i in a.indices) {
                if (isSubsequence(a[i].norm, q)) tiers[i] = 3
            }
        }
        for (t in 0..3) {
            for (i in a.indices) {
                if (tiers[i].toInt() == t) {
                    hits[n] = i
                    n++
                }
            }
        }
        count = n
    }

    private fun tierOf(s: String, q: String): Int {
        if (s.startsWith(q)) return 0
        var p = s.indexOf(q)
        if (p < 0) return 4
        while (p >= 0) {
            if (p == 0 || s[p - 1] == ' ') return 1
            p = s.indexOf(q, p + 1)
        }
        return 2
    }

    private fun isSubsequence(s: String, q: String): Boolean {
        var j = 0
        for (c in s) {
            if (j < q.length && c == q[j]) j++
        }
        return j == q.length
    }

    companion object {
        fun normalize(s: String): String {
            val d = Normalizer.normalize(s, Normalizer.Form.NFD)
            val sb = StringBuilder(d.length)
            for (c in d) {
                if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                    sb.append(c.lowercaseChar())
                }
            }
            return sb.toString()
        }
    }
}
