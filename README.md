# Permaworld

Este es el punto de entrada a la documentación y los proyectos de Permaworld. Aquí no se publica un mod instalable: cada componente tiene su propio repositorio, instrucciones y versiones.

| Proyecto | Qué contiene | Enlaces |
| --- | --- | --- |
| **Permaworld Main** | Mod base para Minecraft Fabric: utilidades, inventario, cultivos y perfiles de paquetes de recursos. | [Código y documentación](https://github.com/EriaLabsStudios/permaworld-main) · [Descargas](https://github.com/EriaLabsStudios/permaworld-main/releases) |
| **Permaworld Chat** | Mod de chat para Fabric 26.3. La primera release permite colorear mensajes; otras funciones siguen en desarrollo. | [Código y seguimiento](https://github.com/EriaLabsStudios/permaworld-chat) · [Descargas](https://github.com/EriaLabsStudios/permaworld-chat/releases) |
| **Permaworld Web** | Consola web y API integradas en Minecraft. Requiere Permaworld Main. | [Código y documentación](https://github.com/EriaLabsStudios/permaworld-web) |
| **Permaworld Texture Packs** | Fuentes editables y paquetes de recursos. | [Código y documentación](https://github.com/EriaLabsStudios/permaworld-texture-packs) |

## Instalación

Elige en las [releases de Permaworld Main](https://github.com/EriaLabsStudios/permaworld-main/releases) un JAR que indique tu versión de Minecraft e instálalo con Fabric y Fabric API. Si quieres la consola web, consulta las instrucciones de **Permaworld Web** y usa una versión compatible con Main. Los paquetes de recursos se distribuyen por separado.

Los repositorios de los componentes son privados por ahora: sus enlaces requieren acceso a EriaLabsStudios.

## Código anterior

Este repositorio alojó una versión antigua del mod para Minecraft 26.1.2, con la web incluida. Su historial de Git se conserva como referencia; el código actual de Main y Web se mantiene en los repositorios enlazados arriba.

## Tags de los mods

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
Para leer los mods privados, configurar `BUNDLE_REPOS_TOKEN` con acceso a los
seis repositorios, o bien `BUNDLE_ERIA_REPOS_TOKEN` para los cuatro de
EriaLabsStudios y `BUNDLE_ADAN_REPOS_TOKEN` para los dos de AdanJoGoHe.
Los tokens solo necesitan permiso de lectura del contenido. Si se configuran
los secretos por propietario, tienen prioridad sobre `BUNDLE_REPOS_TOKEN`.

Actualmente el lote incluye Permaworld Main, Utilities, Chat, Server
Changelogs, Multiworld y Web. Los proyectos locales sin remoto configurado
requieren un repositorio accesible y tags con este formato antes de poder
sumarse. `mods/_skeleton` es una plantilla y `mods/permaworld-profiler` no
tiene build Gradle.
