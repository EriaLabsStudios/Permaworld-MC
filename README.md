# Permaworld

Este es el punto de entrada a los mods de Permaworld. Este repositorio no es un
mod: publica [releases conjuntas](https://github.com/EriaLabsStudios/Permaworld-MC/releases)
con los JAR de los siete componentes, mientras que cada uno conserva su propio
repositorio, instrucciones y versiones.

| Mod | Qué contiene actualmente | Dónde se instala | Enlaces |
| --- | --- | --- | --- |
| **Permaworld Main** | Inventarios, slots favoritos, cosecha y actualizador de mods. | Cliente; algunas funciones también pueden usarse en servidor. | [Repositorio](https://github.com/EriaLabsStudios/permaworld-main) · [Descargas](https://github.com/EriaLabsStudios/permaworld-main/releases) |
| **Permaworld Texture Pack** | Perfiles, ordenación y gestión de paquetes de recursos desde el menú vanilla. | Solo cliente. | [Repositorio](https://github.com/EriaLabsStudios/permaworld-texture-pack) · [Descargas](https://github.com/EriaLabsStudios/permaworld-texture-pack/releases) |
| **Permaworld Utilities** | Corrección de Easy Place de Litematica para colocar bloques con la orientación prevista. | Solo cliente; útil si usas Litematica. | [Repositorio](https://github.com/EriaLabsStudios/permaworld-utilities) · [Descargas](https://github.com/EriaLabsStudios/permaworld-utilities/releases) |
| **Permaworld Chat** | Colores en los mensajes públicos del chat. | Servidor para aplicar el formato; cliente opcional. | [Repositorio](https://github.com/EriaLabsStudios/permaworld-chat) · [Descargas](https://github.com/EriaLabsStudios/permaworld-chat/releases) |
| **Server Changelogs** | Historial de novedades y editor para operadores mediante diálogos del juego. | Servidor o mundo local; los clientes no necesitan el mod. | [Repositorio](https://github.com/EriaLabsStudios/permaworld-server-changelogs) · [Descargas](https://github.com/EriaLabsStudios/permaworld-server-changelogs/releases) |
| **Multiworld** | Opción de impedir que la TNT dañe bloques, desactivada por defecto. | Solo servidor. | [Repositorio](https://github.com/EriaLabsStudios/permaworld-multiworld) · [Descargas](https://github.com/EriaLabsStudios/permaworld-multiworld/releases) |
| **Permaworld Web** | Consola web con estadísticas, historial, inventarios y mapa. Requiere Main. | Servidor o mundo local. | [Repositorio](https://github.com/EriaLabsStudios/permaworld-web) · [Descargas](https://github.com/EriaLabsStudios/permaworld-web/releases) |

## ¿Qué incluye Utilities?

Por ahora incluye **solo Easy Place**. Cuando Litematica calcula dónde colocar
un bloque de un esquema, Utilities conserva el punto exacto de clic en el
paquete enviado al juego y aplica estrategias para losas, escaleras,
trampillas, puertas, troncos, bloques con orientación horizontal, palancas y
raíles. Si no hay una estrategia aplicable, conserva el comportamiento de
Litematica. Sin Litematica no aporta una función visible, así que puedes omitirlo.
No necesita instalarse en el servidor.

Los [proyectos de paquetes de recursos](https://github.com/EriaLabsStudios/permaworld-texture-packs)
se distribuyen por separado y no forman parte del lote de JAR.

## Instalación

Instala Fabric y Fabric API para la misma versión de Minecraft que indique la
[release conjunta](https://github.com/EriaLabsStudios/Permaworld-MC/releases).
En el cliente, instala **Permaworld Main**; añade **Utilities** si utilizas
Easy Place de Litematica y **Texture Pack** para gestionar perfiles de recursos.
Main puede avisarte al iniciar de actualizaciones y ofrecerte instalar los mods
de cliente opcionales. Instala los mods de servidor
en el servidor o en un mundo local según la tabla; no hace falta copiar sus JAR
al cliente para entrar en un servidor Permaworld.

Los repositorios de los componentes son privados por ahora: sus enlaces requieren acceso a EriaLabsStudios.

## Código anterior

Este repositorio alojó una versión antigua del mod para Minecraft 26.1.2, con la web incluida. Su historial de Git se conserva como referencia; el código actual de Main y Web se mantiene en los repositorios enlazados arriba.

## Tags de los mods

## Nombres de los JAR

Todo JAR instalable de Permaworld usa este formato:

`permaworld-<nombre-del-mod>-<version-del-mod>-<version-de-Minecraft>.jar`

Por ejemplo, `permaworld-main-1.0.4-26.3.jar` o
`permaworld-server-changelogs-0.1.1-26.3.jar`. Los JAR de fuentes
conservan el sufijo `-sources` y no se instalan. La Action del lote valida
el nombre antes de publicar una release conjunta.

Cada version publicable de un mod usa un tag anotado con el formato
`vX.Y.Z+mc<version-de-Minecraft>`. `X.Y.Z` es la version del mod y el sufijo
identifica la version **exacta** de Minecraft. Por ejemplo:

- `v1.0.0+mc26.3` y `v1.1.0+mc26.3`: dos versiones del mismo mod para 26.3.
- `v1.0.0+mc26.3.1`: version para 26.3.1, distinta de 26.3.

La release conjunta elige el mayor `X.Y.Z` estable de cada mod para la version
de Minecraft indicada. Compara los numeros de la version, de modo que
`v1.10.0` es posterior a `v1.9.0`. No selecciona prereleases, tags antiguos
como `v26.2` ni tags para otra version de Minecraft. Cada repositorio incluido
debe tener al menos un tag compatible.

Antes de crear un tag, actualizar `mod_version` y `minecraft_version` en
`gradle.properties`, compilar y probar el mod, y confirmar los cambios. Despues,
desde la rama de publicacion del repositorio de ese mod, publicar el commit y
crear y publicar el tag correspondiente:

```bash
git push origin HEAD
git tag -a 'v1.2.0+mc26.3' -m 'Release 1.2.0 para Minecraft 26.3'
git push origin 'v1.2.0+mc26.3'
```

No mover ni reutilizar un tag ya publicado: la release conjunta registra el
commit exacto de cada uno para que el lote sea reproducible. Publicar un tag
puede activar tambien la release individual del mod si ese repositorio tiene
un workflow configurado para ello.

## Publicar una release conjunta

1. Comprobar que **todos** los mods incluidos tienen un tag compatible ya
   publicado en su remoto y que sus builds pasan. La lista actual esta en
   [`scripts/build_bundle.py`](scripts/build_bundle.py), en `REPOSITORIES`.
2. En GitHub, abrir **Permaworld-MC → Actions → Release de mods Permaworld →
   Run workflow**. Indicar la version exacta en `minecraft_version`, por ejemplo
   `26.3` o `26.3.1`.
3. La Action selecciona el mayor tag estable de cada mod, comprueba que
   coincide con `gradle.properties`, compila primero `permaworld-main` y despues
   `permaworld-web`, y publica los JAR en una unica release del padre. Si falta
   un tag o falla un build, termina sin publicar el lote.
4. Revisar la release `bundle-mc<version>-r<numero>`: contiene los JAR,
   `SHA256SUMS.txt` y `manifest.json`, con el tag y commit exactos de cada mod.

El repositorio padre es público: esos JAR y el manifiesto sirven también como
catálogo de actualizaciones para los clientes, sin publicar el código de los
mods. El workflow revisa los tags de Minecraft 26.3 cada hora y solo crea otra
release cuando cambia al menos uno. Una ejecución manual permite publicar un
lote para otra versión exacta de Minecraft.

La Action usa el token normal del repositorio padre para publicar la release.
Para leer los siete mods privados de EriaLabsStudios, configurar
`BUNDLE_REPOS_TOKEN` con permiso de lectura del contenido de todos ellos.

Los siete mods de la tabla forman el lote. Los proyectos locales sin remoto configurado
requieren un repositorio accesible y tags con este formato antes de poder
sumarse. `mods/_skeleton` es una plantilla y `mods/permaworld-profiler` no
tiene build Gradle.
