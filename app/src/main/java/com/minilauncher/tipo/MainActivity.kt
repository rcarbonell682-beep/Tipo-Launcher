package com.minilauncher.tipo

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var repo: AppRepository
    private lateinit var adapter: AppAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var home: View
    private lateinit var drawer: View
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var favContainer: LinearLayout
    private lateinit var search: EditText
    private lateinit var list: ListView

    private val favViews = arrayOfNulls<TextView>(FAV_COUNT)
    private val handler = Handler(Looper.getMainLooper())
    private var pkgReceiver: PackageChangeReceiver? = null
    private var pickSlot = -1
    private var ignoreText = false
    private var downX = 0f
    private var downY = 0f
    private var swipeMin = 0f
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var lastTap = 0L
    private var touchSlop = 0
    private var doubleTapTimeout = 300L

    private val imm: InputMethodManager by lazy {
        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateClock()
        }
    }

    private val reloadTask = Runnable {
        repo.load { onAppsLoaded() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("fav", Context.MODE_PRIVATE)
        swipeMin = 72f * resources.displayMetrics.density
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

        home = findViewById(R.id.home)
        drawer = findViewById(R.id.drawer)
        clockView = findViewById(R.id.clock)
        dateView = findViewById(R.id.date)
        favContainer = findViewById(R.id.favorites)
        search = findViewById(R.id.search)
        list = findViewById(R.id.list)

        repo = AppRepository(packageManager, packageName)
        val inflater = LayoutInflater.from(this)
        adapter = AppAdapter(inflater, repo)
        list.adapter = adapter

        for (i in 0 until FAV_COUNT) {
            val tv = inflater.inflate(R.layout.item_app, favContainer, false) as TextView
            tv.setOnClickListener { onFavClick(i) }
            tv.setOnLongClickListener {
                onFavLong(i)
                true
            }
            favContainer.addView(tv)
            favViews[i] = tv
        }
        refreshHome()

        home.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tapDownX = ev.x
                    tapDownY = ev.y
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(ev.x - tapDownX) > touchSlop || abs(ev.y - tapDownY) > touchSlop
                    if (!moved) {
                        val t = ev.eventTime
                        if (t - lastTap <= doubleTapTimeout) {
                            lastTap = 0L
                            lockScreen()
                        } else {
                            lastTap = t
                        }
                    }
                }
            }
            true
        }

        findViewById<View>(R.id.search_button).setOnClickListener { openDrawer(-1) }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!ignoreText) applyFilter(true)
            }
        })

        search.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                if (search.text.isNotEmpty() && repo.count > 0) {
                    onAppChosen(repo.entryAt(0))
                }
                true
            } else {
                false
            }
        }

        list.setOnItemClickListener { _, _, position, _ ->
            onAppChosen(repo.entryAt(position))
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            showAppMenu(repo.entryAt(position))
            true
        }
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    hideKeyboard()
                }
            }

            override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) {}
        })

        val receiver = PackageChangeReceiver { scheduleReload() }
        pkgReceiver = receiver
        registerReceiver(receiver, PackageChangeReceiver.filter())

        repo.load { onAppsLoaded() }
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter()
        f.addAction(Intent.ACTION_TIME_TICK)
        f.addAction(Intent.ACTION_TIME_CHANGED)
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(tickReceiver, f)
        updateClock()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(tickReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(reloadTask)
        pkgReceiver?.let { unregisterReceiver(it) }
        pkgReceiver = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) closeDrawer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.visibility == View.VISIBLE) closeDrawer()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (drawer.visibility != View.VISIBLE) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (abs(dy) > swipeMin && abs(dy) > abs(dx) * 1.5f) {
                        if (dy < 0f) openDrawer(-1) else expandNotifications()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun scheduleReload() {
        handler.removeCallbacks(reloadTask)
        handler.postDelayed(reloadTask, 400)
    }

    private fun onAppsLoaded() {
        applyFilter(false)
        syncFavorites()
        refreshHome()
    }

    private fun updateClock() {
        val now = System.currentTimeMillis()
        val pattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm"
        clockView.text = DateFormat.format(pattern, now)
        dateView.text = DateFormat.format("EEEE d MMMM", now)
    }

    private class Fav(val pkg: String, val cls: String, val label: String?)

    private fun favOf(slot: Int): Fav? {
        val v = prefs.getString("f$slot", null) ?: return null
        val bar = v.indexOf('|')
        val comp = if (bar < 0) v else v.substring(0, bar)
        val label = if (bar < 0) null else v.substring(bar + 1)
        val s = comp.indexOf('/')
        if (s < 0) return null
        return Fav(comp.substring(0, s), comp.substring(s + 1), label)
    }

    private fun syncFavorites() {
        if (repo.size == 0) return
        for (i in 0 until FAV_COUNT) {
            val f = favOf(i) ?: continue
            val e = repo.find(f.pkg, f.cls)
            if (e == null) {
                prefs.edit().remove("f$i").apply()
            } else if (f.label != e.label) {
                prefs.edit().putString("f$i", e.pkg + "/" + e.cls + "|" + e.label).apply()
            }
        }
    }

    private fun refreshHome() {
        for (i in 0 until FAV_COUNT) {
            val tv = favViews[i] ?: continue
            val f = favOf(i)
            if (f != null) {
                tv.text = f.label ?: repo.find(f.pkg, f.cls)?.label ?: "…"
                tv.setTextColor(COLOR_WHITE)
            } else {
                tv.text = "+"
                tv.setTextColor(COLOR_GRAY)
            }
        }
    }

    private fun onFavClick(slot: Int) {
        val f = favOf(slot)
        if (f != null) launchComponent(f.pkg, f.cls) else openDrawer(slot)
    }

    private fun onFavLong(slot: Int) {
        if (favOf(slot) == null) {
            openDrawer(slot)
            return
        }
        AlertDialog.Builder(this)
            .setItems(arrayOf("Cambiar", "Quitar")) { _, which ->
                if (which == 0) {
                    openDrawer(slot)
                } else {
                    prefs.edit().remove("f$slot").apply()
                    refreshHome()
                }
            }
            .show()
    }

    private fun applyFilter(autoLaunch: Boolean) {
        val q = AppRepository.normalize(search.text.toString().trim())
        repo.filter(q)
        adapter.notifyDataSetChanged()
        if (autoLaunch && q.isNotEmpty() && repo.count == 1) {
            onAppChosen(repo.entryAt(0))
        }
    }

    private fun onAppChosen(e: AppRepository.Entry) {
        if (pickSlot >= 0) {
            prefs.edit().putString("f$pickSlot", e.pkg + "/" + e.cls + "|" + e.label).apply()
            closeDrawer()
            refreshHome()
        } else {
            launch(e)
        }
    }

    private fun launch(e: AppRepository.Entry) {
        launchComponent(e.pkg, e.cls)
    }

    private fun launchComponent(pkg: String, cls: String) {
        closeDrawer()
        try {
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(pkg, cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(i)
        } catch (ex: Exception) {
        }
    }

    private fun lockScreen() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, LockAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            try {
                val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Solo se usa para apagar la pantalla con doble toque."
                    )
                startActivity(i)
            } catch (ex: Exception) {
            }
        }
    }

    private fun showAppMenu(e: AppRepository.Entry) {
        AlertDialog.Builder(this)
            .setTitle(e.label)
            .setItems(arrayOf("Información de la aplicación", "Desinstalar")) { _, which ->
                try {
                    val uri = Uri.fromParts("package", e.pkg, null)
                    val i = if (which == 0) {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                    } else {
                        Intent(Intent.ACTION_DELETE, uri)
                    }
                    startActivity(i)
                } catch (ex: Exception) {
                }
            }
            .show()
    }

    private fun openDrawer(slot: Int) {
        pickSlot = slot
        search.hint = if (slot >= 0) "favorita " + (slot + 1) else "buscar"
        home.visibility = View.GONE
        drawer.visibility = View.VISIBLE
        applyFilter(false)
        list.setSelection(0)
        search.requestFocus()
        search.post { imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        hideKeyboard()
        ignoreText = true
        search.setText("")
        ignoreText = false
        pickSlot = -1
        drawer.visibility = View.GONE
        home.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun expandNotifications() {
        try {
            val sbs = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(sbs)
        } catch (ex: Exception) {
        }
    }

    companion object {
        private const val FAV_COUNT = 6
        private val COLOR_WHITE = 0xFFFFFFFF.toInt()
        private val COLOR_GRAY = 0xFF888888.toInt()
    }
}
