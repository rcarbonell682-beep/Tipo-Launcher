package com.minilauncher.tipo

import rikka.shizuku.Shizuku

/** Ejecuta comandos de shell con los permisos de Shizuku. */
object Sh {
    fun run(cmd: String): String {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", "($cmd) 2>&1"), null, null) as Process
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            out
        } catch (t: Throwable) {
            "Error: $t"
        }
    }
}
