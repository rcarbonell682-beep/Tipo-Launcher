package com.minilauncher.tipo

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView

/**
 * Pantalla de bienvenida: guía rápida de la app y botón para ponerla como launcher
 * predeterminado. Es la única actividad con icono, así que aparece el botón "Abrir"
 * al terminar de instalar. Se puede volver a abrir desde el panel ("guía").
 */
class SetupActivity : Activity() {

    private class Page(val title: String, val body: String)

    private val pages = arrayOf(
        Page(
            "tipo",
            "Un launcher hecho solo de texto: sin imágenes, sin anuncios y sin internet. " +
                "Rápido y ligero.\n\nTe enseño lo básico en un minuto."
        ),
        Page(
            "inicio",
            "En la pantalla de inicio ves la hora, la fecha y tus 6 favoritas.\n\n" +
                "Toca un \"+\" para elegir una app. Mantén pulsada una favorita para " +
                "cambiarla o quitarla."
        ),
        Page(
            "todas tus apps",
            "Desliza hacia arriba o toca \"buscar\".\n\n" +
                "Escribe y las apps se filtran al instante; si solo queda una, se abre sola. " +
                "Mantén pulsada una app para ver su información o desinstalarla."
        ),
        Page(
            "gestos",
            "Desliza hacia abajo: abre las notificaciones.\n\n" +
                "Doble toque en la pantalla de inicio: apaga la pantalla. La primera vez " +
                "Android te pedirá activar el administrador del dispositivo; solo se usa " +
                "para bloquear la pantalla."
        ),
        Page(
            "optimizador",
            "Mantén pulsada la hora para abrir el panel de optimización: cerrar apps, " +
                "limpiar caché, bloquear segundo plano, desactivar bloatware, ahorro " +
                "extremo y forzar 4G.\n\n" +
                "Estas funciones necesitan la app Shizuku activa. El launcher funciona " +
                "perfectamente sin ella."
        ),
        Page(
            "listo",
            "Para usarlo como tu pantalla de inicio tiene que ser el launcher " +
                "predeterminado."
        )
    )

    private var index = 0
    private var useSettings = false

    private lateinit var step: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var status: TextView
    private lateinit var skip: TextView
    private lateinit var next: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        getSharedPreferences("app", Context.MODE_PRIVATE)
            .edit().putBoolean("tutorial_seen", true).apply()

        step = findViewById(R.id.setup_step)
        title = findViewById(R.id.setup_title)
        body = findViewById(R.id.setup_body)
        status = findViewById(R.id.setup_status)
        skip = findViewById(R.id.setup_skip)
        next = findViewById(R.id.setup_next)

        next.setOnClickListener { onNext() }
        skip.setOnClickListener {
            index = if (index == 0) pages.size - 1 else index - 1
            show()
        }
        show()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (index > 0) {
            index--
            show()
        } else {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_ROLE) return
        if (isDefaultHome()) {
            goHome()
        } else {
            useSettings = true
            refreshState()
        }
    }

    private fun show() {
        val p = pages[index]
        step.text = "${index + 1} / ${pages.size}"
        title.text = p.title
        body.text = p.body
        skip.text = if (index == 0) "omitir" else "atrás"
        refreshState()
    }

    private fun refreshState() {
        val last = index == pages.size - 1
        if (!last) {
            status.visibility = View.GONE
            next.text = "siguiente"
            return
        }
        status.visibility = View.VISIBLE
        if (isDefaultHome()) {
            status.text = "Ya es tu launcher predeterminado."
            next.text = "ir al inicio"
        } else {
            status.text = if (useSettings) {
                "No se pudo activar desde aquí. Toca de nuevo para abrir los ajustes " +
                    "de apps predeterminadas y elige Tipo como app de inicio."
            } else {
                "Android te mostrará un cuadro para elegirlo."
            }
            next.text = "establecer como launcher"
        }
    }

    private fun onNext() {
        if (index < pages.size - 1) {
            index++
            show()
            return
        }
        if (isDefaultHome()) goHome() else askDefault()
    }

    @Suppress("DEPRECATION")
    private fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    @Suppress("DEPRECATION")
    private fun askDefault() {
        if (!useSettings && Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE)
                    return
                }
            } catch (e: Exception) {
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        private const val REQ_ROLE = 7
    }
}
