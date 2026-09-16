# SCREENMIX — Contexto y memoria del proyecto

> Última actualización: 2026-09-15
>
> Este archivo es la memoria de producto y técnica del proyecto. Debe actualizarse cuando se tome una decisión importante, se descarte una idea o se confirme una limitación técnica.

## 1. Visión

SCREENMIX nace de una frustración concreta con las apps de captura actuales: muchas son visualmente pobres, esconden funciones útiles tras pagos, la captura larga puede seguir desplazándose sin detectar correctamente el final y, sobre todo, obligan a guardar capturas en la galería aunque el usuario solo quiera compartirlas una vez.

La intención no es crear “otra app de screenshots”, sino una herramienta de captura que se sienta integrada al teléfono: rápida, discreta, bonita y con varios modos de captura accesibles desde un único atajo.

## 2. Principios del producto

- No depender de un botón flotante permanente.
- El botón flotante puede existir como opción para quien lo quiera, pero no debe ser obligatorio ni la experiencia principal.
- La activación ideal debe sentirse como una función nativa del teléfono.
- Un solo gesto/atajo debe poder abrir un selector de modos de captura.
- Compartir una captura no debe implicar necesariamente guardarla en la galería.
- El procesamiento debería ser local siempre que sea posible.
- Evitar servicios de pago, servidores o APIs innecesarias.
- Reutilizar código open source funcional cuando sea legal y técnicamente sensato, en lugar de reinventar motores ya resueltos.

## 3. Flujo de usuario deseado

### Activación

La idea actual es ofrecer varios métodos configurables que lleven al mismo menú:

- Atajo con botones físicos si Android lo permite sin romper el comportamiento normal del teléfono.
- Gesto configurable desde pantalla/borde si puede implementarse sin interferir con el uso táctil normal.
- Acceso desde Ajustes rápidos como alternativa.
- Botón flotante opcional, desactivable.

La captura nativa del fabricante debería seguir funcionando cuando sea posible. La app no debe depender de sustituir por completo la combinación nativa de captura del sistema.

### Selector principal

Al activar SCREENMIX aparece un menú pequeño, limpio y elegante con cuatro opciones:

1. **Captura**
   - Captura normal de lo visible en pantalla.

2. **Captura amplia**
   - Hace scroll automáticamente.
   - Toma capturas sucesivas.
   - Detecta contenido repetido.
   - Une solo el contenido nuevo.
   - Detecta cuándo ya no hay desplazamiento real.
   - Se detiene sola al llegar al final.

3. **Captura web**
   - Modo aún por definir con precisión.
   - Debe decidirse si significa:
     - capturar una página completa a partir de una URL/render web propio, o
     - una captura larga optimizada para navegadores/WebView.

4. **Personalizada**
   - Permite seleccionar una región antes de finalizar la captura.
   - La interfaz ideal es un rectángulo ajustable; después puede evaluarse soporte para otras formas.

## 4. Flujo posterior a la captura

Requisito importante:

- La captura NO debería guardarse automáticamente en la galería si el usuario solo quiere compartirla.

Flujo deseado:

`capturar -> preview/acciones -> compartir / guardar / editar / eliminar`

Si el usuario elige **Compartir** sin guardar:

- la imagen debería vivir en caché o almacenamiento privado temporal;
- compartirse mediante una URI segura;
- poder eliminarse después sin dejar basura en la galería.

Si elige **Guardar**, entonces sí debe insertarse en MediaStore/Galería.

## 5. Activación: decisiones y limitaciones conocidas

### Lo que NO se debe asumir

Una app Android normal no puede garantizar que sustituirá totalmente la combinación de captura nativa del fabricante (por ejemplo, Encendido + Volumen -) porque ese comportamiento suele pertenecer a SystemUI o a una app privilegiada del sistema.

### Gestos

Los gestos globales arbitrarios de 2/3/4 dedos mediante AccessibilityService pueden alterar el comportamiento táctil normal si requieren modos de accesibilidad como exploración táctil. Por eso no deben elegirse como solución principal sin probar la experiencia real.

### Botones físicos

Se ha considerado usar combinaciones de volumen como acceso a SCREENMIX, pero debe probarse en dispositivo real. No se debe diseñar toda la arquitectura suponiendo que se podrán interceptar todas las combinaciones físicas en cualquier fabricante.

## 6. Investigación: SnapTidy 1.2

Se realizó una inspección estática de `SnapTidy 1.2.apk+`. No se instaló ni ejecutó código.

### Identificación

- Paquete: `au.com.grapheme.snaptidy`
- Versión: 1.2
- versionCode: 3
- Android mínimo: API 32
- Target SDK: API 37
- Compile SDK: API 37

### Arquitectura relevante

Componentes encontrados:

- `MainActivity`
- `ScreenshotAccessibilityService`
- `CaptureTileService`
- `CropProfileTileService`
- `BootReceiver`
- `FileProvider`

El AccessibilityService declara:

- `canTakeScreenshot=true`
- `canPerformGestures=true`
- `canRetrieveWindowContent=false`
- `canRequestFilterKeyEvents=false`

Por tanto, SnapTidy usa la API oficial de AccessibilityService para tomar capturas y `dispatchGesture()` para desplazar. No necesita MediaProjection para la captura estática principal.

### Captura larga inteligente

SnapTidy sí implementa un motor real de captura larga:

1. Captura un frame.
2. Hace un gesto aproximadamente desde 80 % a 30 % de la altura.
3. Espera unos 650 ms.
4. Captura el siguiente frame.
5. Convierte imágenes a gris con OpenCV.
6. Detecta áreas fijas superiores e inferiores.
7. Hace template matching en varias franjas del contenido.
8. Calcula la mediana del desplazamiento.
9. Añade solo la sección nueva.
10. Si el avance es inferior a ~6 px dos veces seguidas, termina.
11. También puede terminar por baja confianza, cambio de tamaño o al alcanzar 24 frames.

Puntos técnicos observados:

- Umbral mínimo de coincidencia aproximado: 0.55.
- Franjas probadas alrededor de 10 %, 22 %, 36 % y 52 % del contenido.
- Intenta evitar errores con filas o contenido repetitivo.

Conclusión: la detección de final es visual y real; no es un scroll infinito que dependa de que el usuario lo detenga.

### Limitación relevante

Antes de guardar una captura larga, SnapTidy puede reducirla si supera aproximadamente 2 millones de píxeles. Las capturas muy largas pueden perder resolución.

### Almacenamiento

SnapTidy no resuelve bien el requisito central de SCREENMIX:

- Guarda primero capturas normales y largas en MediaStore.
- Ruta típica: `Pictures/SnapTidy/<categoria>`.
- Después ofrece compartir/editar/eliminar.
- Compartir usa la URI de una imagen ya guardada.
- Existe una preferencia `auto_delete_after_share`, pero no se encontró lógica funcional que la ejecute en esta versión.

Resultado: **no implementa realmente “capturar -> compartir sin guardar en galería”.**

### Funciones adicionales observadas

Incluye Kotlin, Jetpack Compose, Material 3, Room, DataStore, OpenCV, CameraX, ML Kit, TensorFlow Lite, MediaPipe, Media3 y modelos de IA/imagen. También tiene editor, collage, PDF, OCR, eliminación de objetos, mejora de imagen y perfiles de recorte.

### Pagos y trackers

- Google Play Billing 9.0.0.
- Producto: `premium_features`.
- Compra única, no suscripción.
- Se observaron límites gratuitos para algunas funciones.
- No se encontró SDK de anuncios.
- No se encontró evidencia directa de Firebase Analytics, Crashlytics, Sentry, AppsFlyer, Mixpanel, Adjust, etc.
- No declara permiso INTERNET.

### Comparación con SCREENMIX

| Función | SnapTidy 1.2 |
|---|---|
| Captura normal | Sí |
| Captura amplia inteligente | Sí |
| Detección automática del final | Sí |
| Ajustes rápidos | Sí |
| Shake/back tap | Sí |
| Botón flotante | Sí |
| Botones de volumen | No encontrado |
| Gesto de tres dedos | No encontrado |
| Atajo -> menú de cuatro modos | No |
| Captura web completa | No encontrada |
| Captura personalizada previa | Parcial; recorte posterior/perfiles |
| Compartir sin guardar | No |
| Acciones posteriores | Sí |

## 7. Investigación: app de sistema ZTE / RedMagic 10S Pro

Se inspeccionó estáticamente `ZteScreenshot_MFV_abroad.apk`, correspondiente a la app de captura del RedMagic 10S Pro. No se instaló ni ejecutó.

### Identificación

- Paquete: `com.android.ztescreenshot`
- Nombre interno: `ScreenCapture`
- Versión: `16.1.000.000.2604201937`
- versionCode: 160000
- Android mínimo: SDK 31
- Target/compile: SDK 36

### Naturaleza privilegiada

Es una app del sistema:

- usa `android.uid.system`;
- accede a `READ_FRAME_BUFFER`;
- usa ventanas y APIs internas;
- está firmada por ZTE.

Una copia instalada como APK normal no tendría esos privilegios. No debe tomarse como arquitectura directamente replicable por una app de Play Store o APK común.

### Componentes relevantes

Actividades:

- `ScreenshotActivity`
- `ScreenshotEditorActivity`
- `AreaScreenshot`
- `CropImage`
- `RecordActivity`
- `RecordQualityActivity`

Servicios:

- `CropImageService`
- `RecordscreenService`

Receptores:

- `CaptureReceiver`
- receptores de compartir/eliminar/grabación
- `DebugReceiver`

No se encontró TileService propio; el acceso rápido probablemente depende de SystemUI del firmware.

### Captura normal

No usa AccessibilityService para la captura estática principal. Usa APIs privilegiadas del sistema como:

- `IWindowManager.captureDisplay`
- `SurfaceControl`
- acceso a framebuffer

MediaProjection se utiliza para grabación, no como mecanismo principal de screenshot.

### Captura larga

Tiene dos estrategias:

#### A. Basada en estructura de la pantalla

Reconoce tipos como:

- ScrollView
- RecyclerView
- AbsListView
- NestedScrollView
- WebView

Puede consultar rango/posición y desplazar directamente vistas o simular gestos.

También contiene adaptaciones para apps/navegadores concretos como Chrome, Brave, Edge, UC, Tencent, Facebook React, WeChat y Alipay.

#### B. Motor visual SuperSnap

- compara capturas consecutivas;
- usa `libImageProc.so`;
- emplea `PartFind2` y `featureMatch`;
- calcula similitud;
- usa umbrales aproximados 0.80 / 0.85 / 0.90;
- reintenta desplazamiento hasta tres veces;
- detecta rebote y ausencia de avance.

Límites observados:

- vertical: hasta ~8 alturas de pantalla;
- máximo vertical aproximado: 32.000 px;
- horizontal: ~10.000 px.

### Captura personalizada

Soporta:

- rectángulo;
- elipse;
- forma libre cerrada;
- selección asistida por reconocimiento;
- recorte posterior;
- pincel, mosaico y borrador.

### Captura web

No se encontró un motor separado que reciba una URL y renderice una página completa. Lo llamado “web” es esencialmente captura larga sobre WebView/navegadores compatibles.

### Guardado y compartir

El resultado se guarda como JPEG en MediaStore, normalmente bajo:

`Pictures/Screenshots/Screenshot_yyyyMMdd_HHmmss.jpg`

Compartir también sigue el patrón:

`imagen -> guardar -> obtener URI -> ACTION_SEND`

Por tanto, tampoco implementa el requisito de SCREENMIX de compartir sin guardar.

### Pagos / telemetría

- Sin Billing, compras, suscripciones ni anuncios.
- Tiene telemetría propia de ZTE mediante `ZteTrackManager` / servicio del sistema.
- Solicita INTERNET.

### Comparación con SCREENMIX

| Función | ZTE / RedMagic |
|---|---|
| Captura normal | Sí |
| Captura larga | Sí, avanzada |
| Detección de final | Sí |
| Captura web por URL | No |
| Captura larga en navegador | Sí |
| Captura personalizada | Sí |
| Menú previo de cuatro modos | No confirmado |
| Compartir sin guardar | No |
| Atajos del sistema | Sí, con privilegios del firmware |
| Pagos | No |

## 8. Conclusión de la investigación competitiva

La tecnología principal ya existe y está demostrada:

- capturar;
- hacer scroll automático;
- comparar frames;
- detectar contenido fijo;
- calcular desplazamiento;
- unir imágenes;
- detectar el final.

SCREENMIX no necesita inventar desde cero ese motor.

La diferenciación de producto sigue estando en la experiencia:

`atajo invisible -> selector de modo -> captura adecuada -> compartir/guardar según intención`

En especial:

- selector previo de cuatro modos;
- compartir sin ensuciar la galería;
- activación discreta;
- diseño limpio;
- captura larga que se detiene de verdad.

## 9. Open source / referencias técnicas encontradas

Se identificaron proyectos potencialmente útiles para estudiar o reutilizar respetando sus licencias:

### `android-scroll-capture`

Repositorio de ejemplo orientado a captura larga con:

- captura;
- scroll automático;
- comparación visual;
- stitching;
- detección de final.

Se indicó que su licencia es MIT. Antes de copiar código, verificar siempre el repositorio y conservar avisos de licencia requeridos.

### SnapCrop

Proyecto Android con Kotlin + Jetpack Compose + Material 3 y funciones relacionadas con:

- captura larga;
- stitching;
- contenido fijo;
- edición;
- captura web;
- almacenamiento temporal.

Se indicó licencia MIT; verificar antes de reutilizar.

### ScreenshotTile

Referencia para:

- captura mediante AccessibilityService;
- integración con Ajustes rápidos;
- acciones posteriores.

### Volume Button Mapper

Referencia potencial para estudiar cómo detectar/mapear eventos de botones de volumen. Debe probarse en hardware real y no asumirse compatibilidad universal.

### IrisShot

Otra referencia para estudiar captura larga, scroll, MediaProjection/Shizuku y stitching.

## 10. Arquitectura técnica provisional

Nada de esto se considera definitivo todavía, pero la dirección más razonable es:

- Android nativo.
- Kotlin.
- Jetpack Compose para UI.
- AccessibilityService solo donde aporte valor real y dentro de las restricciones de Android/Play.
- Motor de captura larga basado en técnicas ya probadas (comparación visual + stitching), reutilizando open source cuando convenga.
- Caché privada + FileProvider/URI temporal para compartir sin guardar.
- MediaStore únicamente cuando el usuario pulse Guardar.
- Cero backend en la primera versión.
- Cero APIs de pago necesarias para el núcleo.

## 11. Riesgos técnicos

1. **Atajos globales**
   - Los botones físicos no son interceptables de manera uniforme en todos los fabricantes.

2. **Gestos globales**
   - Algunos enfoques de Accessibility pueden interferir con la interacción táctil normal.

3. **Captura larga en apps dinámicas**
   - Vídeos, animaciones, chats en movimiento y contenido que carga tarde pueden romper el matching.

4. **Elementos fijos**
   - Cabeceras, barras inferiores y overlays deben detectarse para evitar duplicados.

5. **Apps seguras**
   - Pantallas protegidas por mecanismos del sistema pueden no permitir captura.

6. **Fragmentación Android**
   - Samsung, Xiaomi, Motorola, ZTE/Nubia, Pixel y otros pueden comportarse distinto.

7. **Políticas de Play Store**
   - Si se usa AccessibilityService, hay que revisar las políticas vigentes antes de publicar.

8. **Resolución/memoria**
   - Capturas largas pueden consumir mucha RAM; hay que evitar límites artificiales de calidad como el downscale observado en SnapTidy salvo que sea estrictamente necesario.

## 12. Funciones aún NO definidas

No asumir decisiones sobre estos puntos hasta discutirlos:

- significado exacto de “Captura web”;
- diseño final del selector;
- gesto principal definitivo;
- combinación física definitiva;
- comportamiento exacto después de una captura normal;
- si habrá editor integrado en V1;
- formatos de salida;
- historial interno de capturas temporales;
- duración de caché temporal;
- comportamiento de borrado automático tras compartir;
- soporte para captura horizontal;
- compatibilidad mínima de Android;
- publicación en Play Store vs distribución directa APK.

## 13. MVP propuesto hasta ahora

El núcleo que tiene más sentido probar primero es:

1. método de activación;
2. selector con cuatro modos;
3. captura normal;
4. captura personalizada;
5. captura amplia con detección automática del final;
6. compartir desde caché sin guardar;
7. guardar en galería solo bajo decisión explícita del usuario.

La captura web puede entrar al MVP solo después de definir exactamente qué significa.

## 14. Regla de trabajo para futuras conversaciones

Antes de añadir funciones nuevas:

1. definir primero la experiencia deseada;
2. comprobar qué permite Android realmente;
3. buscar soluciones open source existentes;
4. comparar con apps reales;
5. reutilizar lo que sea sólido y legal;
6. construir únicamente aquello que aporte diferenciación.

No saltar directamente a implementar funciones no solicitadas.

## 15. Próximos pasos sugeridos

- Definir exactamente “Captura web”.
- Elegir y probar 2–3 mecanismos de activación reales en el RedMagic 10S Pro.
- Crear un prototipo mínimo del selector de cuatro modos.
- Probar compartir mediante caché privada sin MediaStore.
- Integrar o adaptar un motor open source de captura larga.
- Crear una batería de pruebas con Chrome, WhatsApp, Reddit, Instagram, Ajustes y listas largas.
- Registrar en este archivo cada decisión y resultado.

---

### Estado actual

La idea ya está suficientemente definida para iniciar prototipos, pero **todavía no está cerrada**. La prioridad sigue siendo definir primero el comportamiento ideal y después decidir implementación, no al revés.
