# Tipo — launcher tipográfico para Android

Un launcher hecho **solo de texto**: sin iconos, sin imágenes, sin anuncios y **sin internet**.
Pesa muy poco, consume casi nada y está pensado para teléfonos modestos. Incluye un panel de
optimización opcional (con Shizuku) para cerrar apps, limpiar caché, ahorrar batería y más.

> 🤖 **Aviso:** el código de esta app fue escrito **al 100 % con [Claude](https://claude.ai)**
> (IA de Anthropic). Su autor diseñó la idea, fue pidiendo los cambios y la probó en su
> teléfono (Redmi A3x). Úsala con criterio: puede tener errores que aún no se han descubierto.

---

## Qué hace

**Pantalla de inicio**
- Hora y fecha grandes y 6 favoritas.
- Toca un `+` para elegir una app. Mantén pulsada una favorita para cambiarla o quitarla.

**Todas tus apps**
- Desliza hacia arriba (o toca `buscar`) y escribe: la lista se filtra al instante, sin importar
  tildes ni mayúsculas. Si solo queda una app, se abre sola.
- Mantén pulsada una app para ver su información o desinstalarla.

**Gestos**
- Deslizar hacia abajo: notificaciones.
- Doble toque en la pantalla de inicio: apaga la pantalla (pide activar el administrador del
  dispositivo la primera vez; solo se usa para bloquear).
- Mantener pulsada la hora o la fecha: abre el panel de optimización.

**Panel de optimización** *(necesita [Shizuku](https://shizuku.rikka.app/) activo; el launcher
funciona igual sin él)*

| Opción | Qué hace | Comando que ejecuta |
|---|---|---|
| forzar 4G | Abre la pantalla "Información del teléfono" para elegir el tipo de red | busca `RadioInfo` en el sistema y lo abre con `am start` |
| optimizar apps | Recompila todas las apps para que abran más rápido (tarda varios minutos) | `cmd package compile -m speed-profile -a` |
| cerrar apps | Cierra las apps de terceros que no estén protegidas | `am force-stop` + `am kill-all` |
| limpiar caché | Borra la caché de todas las apps y muestra el espacio liberado | `pm trim-caches 999G` |
| bloquear segundo plano | Eliges apps a las que se les quita el segundo plano y el autoinicio | `cmd appops set … RUN_IN_BACKGROUND/RUN_ANY_IN_BACKGROUND ignore`, `am set-standby-bucket … restricted` |
| bloatware | Lista de apps de Xiaomi/Google que suelen sobrar; se desactivan y reactivan con un toque | `pm disable-user --user 0` / `pm enable` |
| ahorro extremo | Ahorro de datos, brillo bajo, cierra y restringe apps. Se revierte con un toque. No toca el ahorro de batería del sistema | `cmd netpolicy set restrict-background`, brillo y apps |
| apps protegidas | Apps que nunca se cierran ni se restringen (además del launcher, el teclado, Shizuku y Google Play, que siempre están protegidos) | — |
| guía | Vuelve a mostrar la guía de bienvenida | — |

Arriba del panel se ve la RAM libre, la temperatura y la batería (se actualiza solo mientras el
panel está abierto, así que no gasta nada en reposo).

> ⚠️ **Cuidado con el ahorro extremo:** el ahorro de datos puede retrasar notificaciones de
> apps que usan datos móviles en segundo plano. Se revierte desde el mismo panel.
> ⚠️ **Bloatware:** la lista está pensada para teléfonos Xiaomi. Todo se puede reactivar desde
> el mismo diálogo, pero desactiva solo lo que sepas que no necesitas.

---

## Instalación

1. Ve a la sección **[Releases](../../releases)** y descarga `Tipo-launcher.apk`.
2. Ábrelo e instálalo (Android te pedirá permitir instalar apps desconocidas).
3. Toca **Abrir**: verás una guía rápida y, al final, el botón **establecer como launcher**.

Las versiones nuevas se instalan **encima** de la anterior sin desinstalar nada.

### Usar el panel de optimización (Shizuku)

1. Instala Shizuku y actívalo (por depuración inalámbrica o con root; mira su
   [guía oficial](https://shizuku.rikka.app/guide/setup/)).
2. En el launcher, mantén pulsada la hora y toca cualquier opción del panel.
3. Acepta el permiso de Shizuku cuando aparezca.

Si Shizuku no está activo, el panel lo indica; tocar ese texto abre la app de Shizuku.

---

## Permisos y privacidad

La app **no tiene permiso de internet**, no recopila datos y no usa servicios de Google.

| Permiso | Para qué |
|---|---|
| `QUERY_ALL_PACKAGES` | Mostrar la lista de apps instaladas (es un launcher) y armar las listas del panel |
| `EXPAND_STATUS_BAR` | Abrir las notificaciones al deslizar hacia abajo |
| `REQUEST_DELETE_PACKAGES` | Mostrar el diálogo de desinstalación de una app |
| Administrador del dispositivo (opcional) | Solo la política "bloquear pantalla", para el doble toque |
| Shizuku (opcional) | Ejecutar los comandos del panel con permisos de shell |

---

## Compilar tú mismo

### Sin ordenador (solo con el teléfono)
1. Haz un **Fork** de este repositorio (botón *Fork* en GitHub).
2. En tu fork, entra en **Actions** y activa los workflows.
3. Cada vez que subas un cambio se compila solo. Descarga el APK en
   *Actions → la ejecución → Artifacts* (viene dentro de un ZIP).
4. Para publicar el APK en *Releases*, crea una etiqueta que empiece por `v`
   (por ejemplo `v1.0.1`): el workflow la compila y la adjunta automáticamente.

### Con ordenador
```
gradle assembleRelease
```
Necesitas JDK 17 y el SDK de Android (plataforma 34). El APK queda en
`app/build/outputs/apk/release/`.

---

## Estructura del proyecto

```
app/src/main/java/com/minilauncher/tipo/
├── MainActivity.kt        Pantalla de inicio, cajón de apps, gestos
├── AppRepository.kt       Carga, ordena y filtra las apps
├── AppAdapter.kt          Lista del cajón
├── Optimizer.kt           Panel de optimización (todas las funciones de Shizuku)
├── Sh.kt                  Ejecuta comandos de shell a través de Shizuku
├── SetupActivity.kt       Guía de bienvenida y botón de launcher predeterminado
├── PackageChangeReceiver.kt   Refresca la lista al instalar/desinstalar apps
└── LockAdminReceiver.kt   Administrador de dispositivo (solo bloquear pantalla)
```
Sin Compose ni librerías de interfaz: vistas clásicas de Android y Kotlin. La única dependencia es la API de Shizuku.

---

## Para desarrolladores

Si te gusta la app, **continúa su desarrollo**: haz un fork, cámbiala a tu gusto y envía un
pull request o publica tu propia versión. La licencia es MIT, así que puedes copiarla,
modificarla y redistribuirla libremente.

Ideas pendientes:
- Pantalla de ajustes (número de favoritas, colores, tamaño de la hora).
- Traducción a más idiomas (los textos están en el código de `SetupActivity.kt` y `Optimizer.kt`).
- Mosaico en ajustes rápidos para el ahorro extremo.
- Listas de bloatware para otras marcas (Samsung, Realme, Motorola…).
- Ocultar apps del cajón.
- Pruebas automáticas.

Cosas que debes saber:
- `Sh.kt` llama por reflexión al método privado `Shizuku.newProcess`, con la versión **13.1.5** de la
  API. Si actualizas Shizuku, comprueba que sigue funcionando (hay reglas de ProGuard en
  `app/proguard-rules.pro` para que R8 no lo elimine).
- El APK se firma con `keystore/debug.keystore`, una **clave de depuración pública** (contraseña
  `android`). Sirve para que las compilaciones se actualicen unas sobre otras, pero **no es
  segura**: si publicas tu propia versión, genera tu propia clave y no subas el archivo de claves.
- El botón "forzar 4G" busca la actividad `RadioInfo` en `com.android.phone` y
  `com.android.settings`; en algunos teléfonos el fabricante la oculta o la cambia.

---

## Créditos

- [Shizuku](https://github.com/RikkaApps/Shizuku-API) por RikkaApps (Apache-2.0).
- Código generado al 100 % por Claude (Anthropic) a partir de las indicaciones de su autor.

---

## English summary

**Tipo** is a text-only Android launcher: no icons, no images, no ads, no internet permission.
Home screen with clock, date and 6 favorites; swipe up to search apps; swipe down for
notifications; double tap to lock the screen. Long-press the clock to open an optional
optimization panel (needs [Shizuku](https://shizuku.rikka.app/)): force-stop apps, trim caches,
recompile apps, restrict background activity, disable bloatware, an "extreme saving" mode and a
shortcut to the hidden radio-info screen to force 4G/LTE.

Install the APK from **Releases**; the first-run guide helps you set it as the default launcher.
Build it by forking the repo (GitHub Actions) or with `gradle assembleRelease`.

> All the code in this project was written 100% by Claude (Anthropic) from its author's
> instructions. Developers are welcome to fork it and keep developing it (MIT license).
