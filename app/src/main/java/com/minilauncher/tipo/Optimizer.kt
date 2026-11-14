package com.minilauncher.tipo

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

/** Panel de optimización (Shizuku) que se abre encima del launcher. */
class Optimizer(private val act: Activity, private val panel: View) {

    private class App(val pkg: String, val label: String)

    private val pm: PackageManager = act.packageManager
    private val sp = act.getSharedPreferences("opt", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val exec by lazy { Executors.newSingleThreadExecutor() }

    private val stats: TextView = panel.findViewById(R.id.opt_stats)
    private val shizukuInfo: TextView = panel.findViewById(R.id.opt_shizuku)
    private val ecoItem: TextView = panel.findViewById(R.id.opt_eco)
    private val log: TextView = panel.findViewById(R.id.opt_log)
    private var busy = false

    private val tick = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 2000)
        }
    }

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        handler.post { updateShizuku() }
    }

    val isOpen: Boolean
        get() = panel.visibility == View.VISIBLE

    init {
        bind(R.id.opt_compile) { doCompile() }
        bind(R.id.opt_kill) { doKill() }
        bind(R.id.opt_cache) { doCache() }
        bind(R.id.opt_block) { pickBlock() }
        bind(R.id.opt_bloat) { pickBloat() }
        bind(R.id.opt_eco) { toggleEco() }
        bind(R.id.opt_protected) { pickProtected() }
        bind(R.id.opt_close) { close() }
        Shizuku.addRequestPermissionResultListener(permListener)
        updateEco()
    }

    private fun bind(id: Int, action: () -> Unit) {
        panel.findViewById<View>(id).setOnClickListener { action() }
    }

    // ------------------------------------------------------------ ciclo de vida

    fun open() {
        panel.visibility = View.VISIBLE
        updateShizuku()
        updateEco()
        updateStats()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 2000)
    }

    fun close() {
        if (!isOpen) return
        panel.visibility = View.GONE
        handler.removeCallbacks(tick)
    }

    fun onPause() {
        handler.removeCallbacks(tick)
    }

    fun onResume() {
        if (isOpen) {
            updateShizuku()
            updateStats()
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, 2000)
        }
    }

    fun destroy() {
        handler.removeCallbacks(tick)
        Shizuku.removeRequestPermissionResultListener(permListener)
    }

    // ------------------------------------------------------------ monitor

    private fun updateStats() {
        val am = act.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val bat = act.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        var temp = 0f
        var lvl = -1
        if (bat != null) {
            temp = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            lvl = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        }
        stats.text = "RAM libre ${mi.availMem shr 20} / ${mi.totalMem shr 20} MB\n" +
            "$temp °C · batería $lvl%"
    }

    private fun updateShizuku() {
        shizukuInfo.text = try {
            if (!Shizuku.pingBinder()) {
                "shizuku: no está activo"
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                "shizuku: falta el permiso (toca una opción)"
            } else {
                "shizuku: listo"
            }
        } catch (t: Throwable) {
            "shizuku: no disponible"
        }
    }

    private fun updateEco() {
        ecoItem.text = if (sp.getBoolean("eco", false)) {
            "ahorro extremo: activo (revertir)"
        } else {
            "ahorro extremo"
        }
    }

    private fun toast(s: String) {
        Toast.makeText(act, s, Toast.LENGTH_LONG).show()
    }

    private fun setLog(s: String) {
        log.text = if (s.length > 1500) s.substring(s.length - 1500) else s
    }

    // ------------------------------------------------------------ ejecución

    private fun ready(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                toast("Shizuku no está activo. Ábrelo e inícialo.")
                false
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(100)
                toast("Acepta el permiso de Shizuku y vuelve a tocar la opción.")
                false
            } else {
                true
            }
        } catch (t: Throwable) {
            toast("Shizuku no disponible.")
            false
        }
    }

    private fun runTask(title: String, job: () -> String) {
        if (busy) {
            toast("Espera, hay una tarea en curso.")
            return
        }
        if (!ready()) return
        busy = true
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setLog("$title…")
        exec.execute {
            val r = try {
                job()
            } catch (t: Throwable) {
                "Error: $t"
            }
            handler.post {
                busy = false
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                setLog("$title\n$r")
                updateEco()
                updateStats()
            }
        }
    }

    // ------------------------------------------------------------ listas de apps

    private fun safe(p: String): Boolean = p.matches(SAFE_PKG)

    private fun startsAuto(p: String): Boolean {
        for (x in AUTO_PREFIX) if (p.startsWith(x)) return true
        return false
    }

    private fun strSet(key: String): MutableSet<String> =
        HashSet(sp.getStringSet(key, null) ?: emptySet<String>())

    /** Apps que nunca se tocan: este launcher, otros launchers, teclado, Shizuku, ashell, Google Play. */
    private fun autoSet(): Set<String> {
        val s = HashSet<String>()
        s.add(act.packageName)
        s.add("moe.shizuku.privileged.api")
        s.add("in.sunilpaulmathew.ashell")
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        for (r in pm.queryIntentActivities(home, 0)) s.add(r.activityInfo.packageName)
        val ime = Settings.Secure.getString(act.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        if (ime != null && ime.contains("/")) s.add(ime.substring(0, ime.indexOf('/')))
        return s
    }

    @Suppress("DEPRECATION")
    private fun apps(excludeUserProtected: Boolean): List<App> {
        val auto = autoSet()
        val prot: Set<String> = if (excludeUserProtected) strSet("protected") else emptySet()
        val r = ArrayList<App>()
        for (a in pm.getInstalledApplications(0)) {
            val sys = (a.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val upd = (a.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (sys && !upd) continue
            if (!a.enabled) continue
            if (!safe(a.packageName)) continue
            if (auto.contains(a.packageName) || startsAuto(a.packageName)) continue
            if (prot.contains(a.packageName)) continue
            r.add(App(a.packageName, pm.getApplicationLabel(a).toString()))
        }
        r.sortBy { it.label.lowercase() }
        return r
    }

    private fun multi(
        title: String,
        labels: Array<String>,
        checked: BooleanArray,
        ok: (BooleanArray) -> Unit
    ) {
        val c = checked.clone()
        AlertDialog.Builder(act, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(title)
            .setMultiChoiceItems(labels, c) { _, i, on -> c[i] = on }
            .setPositiveButton("Aplicar") { _, _ -> ok(c) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------ 1. compilar

    private fun doCompile() {
        runTask("Optimizando apps (tarda varios minutos, no cierres la app)") {
            val o = Sh.run("cmd package compile -m speed-profile -a")
            if (o.contains("Success")) {
                "Listo. Todas las apps compiladas."
            } else if (o.length > 400) {
                o.substring(o.length - 400)
            } else {
                o
            }
        }
    }

    // ------------------------------------------------------------ 2. cerrar apps

    private fun doKill() {
        runTask("Cerrando apps") {
            val c = StringBuilder()
            var n = 0
            for (a in apps(true)) {
                c.append("am force-stop ").append(a.pkg).append("; ")
                n++
            }
            c.append("am kill-all")
            Sh.run(c.toString())
            "Listo. $n apps cerradas."
        }
    }

    // ------------------------------------------------------------ 3. caché

    private fun freeBytes(): Long =
        StatFs(Environment.getDataDirectory().path).availableBytes

    private fun doCache() {
        runTask("Limpiando caché") {
            val before = freeBytes()
            Sh.run("pm trim-caches 999G")
            val freed = maxOf(0L, freeBytes() - before) shr 20
            "Listo. Espacio liberado: $freed MB"
        }
    }

    // ------------------------------------------------------------ 4. bloquear segundo plano

    private fun blockCmd(p: String, block: Boolean): String {
        return if (block) {
            "cmd appops set $p RUN_IN_BACKGROUND ignore; " +
                "cmd appops set $p RUN_ANY_IN_BACKGROUND ignore; " +
                "cmd appops set $p 10008 ignore; " +
                "am set-standby-bucket $p restricted; " +
                "am force-stop $p; "
        } else {
            "cmd appops set $p RUN_IN_BACKGROUND allow; " +
                "cmd appops set $p RUN_ANY_IN_BACKGROUND allow; " +
                "cmd appops set $p 10008 allow; " +
                "am set-standby-bucket $p active; "
        }
    }

    private fun pickBlock() {
        val list = apps(true)
        if (list.isEmpty()) {
            toast("No hay apps para mostrar.")
            return
        }
        val blocked = strSet("blocked")
        val labels = Array(list.size) { list[it].label }
        val checked = BooleanArray(list.size) { blocked.contains(list[it].pkg) }
        multi("Marcadas = sin segundo plano ni autoinicio", labels, checked) { c ->
            val now = HashSet(blocked)
            val cmd = StringBuilder()
            var add = 0
            var rem = 0
            for (i in list.indices) {
                val p = list[i].pkg
                if (c[i] && !blocked.contains(p)) {
                    cmd.append(blockCmd(p, true))
                    now.add(p)
                    add++
                } else if (!c[i] && blocked.contains(p)) {
                    cmd.append(blockCmd(p, false))
                    now.remove(p)
                    rem++
                }
            }
            if (cmd.isNotEmpty()) {
                val msg = "Listo. Bloqueadas: $add · Liberadas: $rem"
                runTask("Aplicando bloqueo") {
                    Sh.run(cmd.toString())
                    sp.edit().putStringSet("blocked", now).apply()
                    msg
                }
            }
        }
    }

    // ------------------------------------------------------------ 5. bloatware

    @Suppress("DEPRECATION")
    private fun pickBloat() {
        val pk = ArrayList<String>()
        val lb = ArrayList<String>()
        val wasOff = ArrayList<Boolean>()
        for (p in BLOAT) {
            try {
                val a = pm.getApplicationInfo(p, 0)
                pk.add(p)
                lb.add("${pm.getApplicationLabel(a)}  ($p)")
                wasOff.add(!a.enabled)
            } catch (e: PackageManager.NameNotFoundException) {
            }
        }
        if (pk.isEmpty()) {
            toast("No se encontró bloatware conocido.")
            return
        }
        val labels = Array(lb.size) { lb[it] }
        val checked = BooleanArray(pk.size) { wasOff[it] }
        multi("Marcadas = desactivadas (se pueden reactivar)", labels, checked) { c ->
            val cmd = StringBuilder()
            var off = 0
            var on = 0
            for (i in pk.indices) {
                val p = pk[i]
                if (c[i] && !wasOff[i]) {
                    cmd.append("pm disable-user --user 0 ").append(p).append("; ")
                    off++
                } else if (!c[i] && wasOff[i]) {
                    cmd.append("pm enable ").append(p).append("; ")
                    on++
                }
            }
            if (cmd.isNotEmpty()) {
                val s = cmd.toString()
                val msg = "Listo. Desactivadas: $off · Reactivadas: $on"
                runTask("Aplicando cambios") {
                    Sh.run(s)
                    msg
                }
            }
        }
    }

    // ------------------------------------------------------------ 6. ahorro extremo

    private fun toggleEco() {
        if (sp.getBoolean("eco", false)) ecoOff() else ecoOn()
    }

    private fun ecoOn() {
        runTask("Activando ahorro extremo") {
            try {
                sp.edit()
                    .putInt("b", Settings.System.getInt(act.contentResolver, "screen_brightness", 100))
                    .putInt("bm", Settings.System.getInt(act.contentResolver, "screen_brightness_mode", 0))
                    .apply()
            } catch (t: Throwable) {
            }
            val blocked = strSet("blocked")
            val eco = HashSet<String>()
            val c = StringBuilder()
            c.append("settings put global low_power 1; ")
            c.append("cmd netpolicy set restrict-background true; ")
            c.append("settings put system screen_brightness_mode 0; ")
            c.append("settings put system screen_brightness 30; ")
            for (a in apps(true)) {
                c.append("am force-stop ").append(a.pkg).append("; ")
                if (!blocked.contains(a.pkg)) {
                    c.append("cmd appops set ").append(a.pkg).append(" RUN_ANY_IN_BACKGROUND ignore; ")
                    c.append("am set-standby-bucket ").append(a.pkg).append(" restricted; ")
                    eco.add(a.pkg)
                }
            }
            c.append("am kill-all")
            Sh.run(c.toString())
            sp.edit().putBoolean("eco", true).putStringSet("eco_apps", eco).apply()
            "Ahorro extremo ACTIVO.\nAhorro de batería y de datos, brillo bajo, apps cerradas y restringidas."
        }
    }

    private fun ecoOff() {
        runTask("Revirtiendo ahorro extremo") {
            val eco = strSet("eco_apps")
            val blocked = strSet("blocked")
            val c = StringBuilder()
            c.append("settings put global low_power 0; ")
            c.append("cmd netpolicy set restrict-background false; ")
            c.append("settings put system screen_brightness_mode ").append(sp.getInt("bm", 0)).append("; ")
            c.append("settings put system screen_brightness ").append(sp.getInt("b", 100)).append("; ")
            for (p in eco) {
                if (!safe(p) || blocked.contains(p)) continue
                c.append("cmd appops set ").append(p).append(" RUN_ANY_IN_BACKGROUND allow; ")
                c.append("am set-standby-bucket ").append(p).append(" active; ")
            }
            Sh.run(c.toString())
            sp.edit().putBoolean("eco", false).remove("eco_apps").apply()
            "Ahorro extremo desactivado. Todo vuelve como estaba."
        }
    }

    // ------------------------------------------------------------ apps protegidas

    private fun pickProtected() {
        val list = apps(false)
        if (list.isEmpty()) {
            toast("No hay apps para mostrar.")
            return
        }
        val prot = strSet("protected")
        val labels = Array(list.size) { list[it].label }
        val checked = BooleanArray(list.size) { prot.contains(list[it].pkg) }
        multi("Marcadas = protegidas (no se cierran ni restringen)", labels, checked) { c ->
            val now = HashSet<String>()
            for (i in list.indices) if (c[i]) now.add(list[i].pkg)
            sp.edit().putStringSet("protected", now).apply()
            setLog("Apps protegidas guardadas: ${now.size}")
        }
    }

    companion object {
        private val SAFE_PKG = Regex("[A-Za-z0-9._]+")

        // Paquetes de Xiaomi/Google que suelen sobrar; solo se muestran los que existen en el teléfono.
        private val BLOAT = arrayOf(
            "com.miui.analytics",
            "com.miui.msa.global",
            "com.xiaomi.mipicks",
            "com.miui.bugreport",
            "com.miui.hybrid",
            "com.miui.hybrid.accessory",
            "com.mi.globalminusscreen",
            "com.miui.videoplayer",
            "com.miui.player",
            "com.miui.yellowpage",
            "com.miui.android.fashiongallery",
            "com.xiaomi.glgm",
            "com.google.android.videos",
            "com.google.android.apps.tachyon"
        )

        private val AUTO_PREFIX = arrayOf(
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.webview"
        )
    }
}
