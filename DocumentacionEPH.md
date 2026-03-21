# Eduzzepunks Productive Hoe - Documentacion Tecnica

## 1) Resumen
Mod para Forge 1.20.1 orientado a agricultura y exploracion ligera.
- Cosecha inteligente con azadas y replantado automatico.
- Encantamientos propios (`Acreage`, `Bountiful Seed`).
- Limpieza de maleza por area con click derecho.
- Sistema de fatiga del suelo (monocultivo) con recuperacion por rotacion o bonemeal.
- UI opcional (overlay propio + compatibilidad con Jade) y nota informativa en JEI.
- Estructuras jigsaw de superficie con loot personalizada.

## 2) Encantamientos

### 2.1 Acreage (`acreage`)
- Tipo: solo azadas.
- Nivel maximo tecnico: III.
- Traducciones:
  - EN: `Acreage`
  - ES: `Acreaje`

#### Obtencion por mesa de encantamientos
- Nivel I y II: si.
- Nivel III: no (bloqueado subiendo el costo de nivel III a rango imposible).

#### Efecto sin SHIFT
Cosecha por area centrada en el cultivo clicado:
- Nivel I: `3x3`
- Nivel II: `5x5`
- Nivel III: `7x7`

Reglas:
- No requiere mismo tipo de cultivo.
- Solo cosecha `CropBlock` maduros.

#### Efecto con SHIFT
Mantiene cosecha por linea, con limite ampliado por nivel:
- Madera: `3 -> 5 -> 7`
- Piedra: `5 -> 7 -> 10`
- Hierro: `7 -> 12 -> 15`
- Diamante: `9 -> 14 -> 18`
- Netherite: `11 -> 15 -> 20`

(orden: nivel I, II, III)

### 2.2 Bountiful Seed (`bountiful_seed`)
- Tipo: solo azadas.
- Nivel maximo tecnico: III.
- Traducciones:
  - EN: `Bountiful Seed`
  - ES: `Semilla Prodiga`

#### Obtencion por mesa de encantamientos
- Nivel I y II: si.
- Nivel III: no (bloqueado en mesa).

#### Efecto A: no consumir semilla/cultivo de replantado
Probabilidad por cultivo cosechado:
- Nivel I: 15%
- Nivel II: 25%
- Nivel III: 40%

Aplica tambien a cultivos donde semilla y producto son el mismo item (zanahoria/papa).

#### Efecto B: bonus estilo fortuna para cultivos
- Aplica un bonus con la misma probabilidad que Fortuna en minerales.
- Excluye semillas cuando son item separado.
- En cultivos de item unico (zanahoria/papa), el bonus se aplica al rendimiento util.

## 3) Cosecha y durabilidad

### 3.1 Cosecha de cultivos
- Click derecho con azada sobre cultivo maduro:
  - cosecha,
  - drops vanilla,
  - replantado automatico a edad 0.
- Sin SHIFT: si hay `Acreage`, cosecha por area.
- Con SHIFT: cosecha por linea con el limite del tier + `Acreage`.

### 3.2 Durabilidad de cosecha de cultivos
Por cada cultivo cosechado:
- 30% de probabilidad de consumir 1 punto de durabilidad.
- 70% de no consumir.

## 4) Fatiga del suelo (Soil Fatigue)

### 4.1 Datos por bloque de farmland
Cada bloque de farmland guarda:
- `soilFatigue` (0 a 5)
- `lastCropType` (ResourceLocation del ultimo cultivo)
- `rotationBonus` (ticks de bonus por rotacion)

Se guarda en `SavedData` por dimension. No se usan BlockEntities.

### 4.2 Generacion al replantar
Solo ocurre al replantar (por azada o al plantar manualmente un cultivo en edad 0).

Regla:
```
if (currentCrop == lastCropType) soilFatigue += 1
if (currentCrop != lastCropType) soilFatigue -= 2
clamp(0..5)
lastCropType = currentCrop
```

Si el cultivo cambia, se activa un bonus temporal:
- `rotationBonus = rotationBonusTicks` (configurable).

### 4.3 Penalizacion de crecimiento
En el tick aleatorio de crecimiento, se puede bloquear el crecimiento segun la fatiga.
La penalizacion es una probabilidad de bloquear el tick.

Valores por defecto (`penaltyByFatigue`):
| Fatiga | Tick bloqueado | Calidad aproximada |
| --- | --- | --- |
| 0 | 0% | 100% |
| 1 | 20% | 80% |
| 2 | 40% | 60% |
| 3 | 60% | 40% |
| 4 | 80% | 20% |
| 5 | 95% | 5% |

Mientras `rotationBonus` esta activo, la penalizacion se multiplica por
`rotationBonusPenaltyMultiplier` (por defecto 0.5).

### 4.4 Recuperacion
No hay recuperacion pasiva.
Solo ocurre por:
- Rotacion de cultivos: plantar un cultivo distinto reduce fatiga en 2 y activa el bonus.
- Fertilizacion: usar bonemeal directamente en farmland (sin cultivo arriba) resetea a 0.

Al recuperar fatiga se generan particulas de `happy_villager`.

### 4.5 Reglas importantes
- Romper el cultivo manualmente no resetea la fatiga.
- El crecimiento natural no incrementa fatiga.
- Si el farmland se rompe o se pisa, los datos se limpian.
- La logica solo corre en cosecha, replantado, bonemeal y ticks de crecimiento.

### 4.6 UI y compat
- Overlay propio: al mirar farmland, muestra fatiga y calidad.
- Jade (opcional, no dependencia): tooltip para farmland y cultivos.
- JEI: nota informativa sobre Soil Fatigue en farmland.

## 5) Limpieza de maleza con azada

Se activa con click derecho sobre bloques del tag:
- `#minecraft:replaceable_plants` (extendido en `data/minecraft/tags/blocks/replaceable_plants.json`)

### 5.1 Direccion
El area se orienta usando `player.getDirection()` (horizontal):
- si mira al norte, limpia hacia norte y se abre hacia este/oeste.
- equivalente para sur/este/oeste.

### 5.2 Area base por tier
Se calcula con `baseWidth` y `baseDepth` por material:
- Madera: `3 x 3`
- Piedra: `5 x 4`
- Hierro: `7 x 5`
- Diamante: `9 x 6`
- Netherite: `11 x 7`

Si la azada tiene `Acreage`:
- `+2` al ancho por nivel.
- `+2` al largo por nivel.

### 5.3 Ejecucion
- Recorre offsets `x,y,z` relativos al bloque clicado.
- Si el bloque pertenece a `replaceable_plants`, se elimina con `world.destroyBlock(pos, false)`.
- Al limpiar correctamente, se dispara feedback de barrido (animacion sweep + particulas + sonido).

### 5.4 Durabilidad de limpieza
- 1 punto de durabilidad por cada 5 bloques eliminados.
- Formula aplicada: `ceil(eliminados / 5)`.

## 6) Estructuras de mundo (Jigsaw)

### 6.1 Tipo y distribucion
Se agregan dos estructuras de superficie (`surface_structures`):
- `abandoned_hut`
- `abandoned_garden`

Biomas:
- `#minecraft:is_overworld`

Distribucion (`random_spread`):
- spacing: 24
- separation: 8

### 6.2 Variantes
Las variantes se eligen en el `template_pool`.

Cabanas:
- `abandoned_hut`, `abandoned_hut_1`, `abandoned_hut_2`, `abandoned_hut_3`

Jardines:
- `abandoned_garden_2`, `abandoned_garden_3`

Notas:
- Los jardines son mas pequenos y las cabanas tienen techo y decoracion mejorados.
- Los archivos `abandoned_garden.nbt` y `abandoned_garden_1.nbt` quedan como legacy si se quieren reactivar.

## 7) Loot
Ambas estructuras tienen barril con loot table personalizada:
- `eduhzzepunks_productive_hoe:chests/abandoned_farming_cache`

Contenido principal:
- 1 libro encantado (100%):
  - `acreage` III o `bountiful_seed` III.
- Comida aleatoria (pan, zanahorias, papas, manzanas, estofados).

## 8) Archivos principales

### Java (logica)
- `src/main/java/com/example/examplemod/ProductiveHoeMod.java`
- `src/main/java/com/example/examplemod/event/FarmingEventHandler.java`
- `src/main/java/com/example/examplemod/event/SoilFatigueEventHandler.java`
- `src/main/java/com/example/examplemod/event/WorldgenDebugEventHandler.java`
- `src/main/java/com/example/examplemod/farming/HarvestLogic.java`
- `src/main/java/com/example/examplemod/farming/CropDetection.java`
- `src/main/java/com/example/examplemod/farming/EnchantmentEffects.java`
- `src/main/java/com/example/examplemod/farming/SoilFatigueManager.java`
- `src/main/java/com/example/examplemod/farming/SoilFatigueEffects.java`
- `src/main/java/com/example/examplemod/util/PlantCleanupUtil.java`
- `src/main/java/com/example/examplemod/enchant/AcreageEnchantment.java`
- `src/main/java/com/example/examplemod/enchant/BountifulSeedEnchantment.java`
- `src/main/java/com/example/examplemod/enchant/ModEnchantments.java`
- `src/main/java/com/example/examplemod/network/ModNetworking.java`
- `src/main/java/com/example/examplemod/network/SoilFatigueRequest.java`
- `src/main/java/com/example/examplemod/network/SoilFatigueResponse.java`

### Cliente y compat
- `src/main/java/com/example/examplemod/client/SoilFatigueClientOverlay.java`
- `src/main/java/com/example/examplemod/compat/jade/SoilFatigueJadePlugin.java`
- `src/main/java/com/example/examplemod/compat/jade/SoilFatigueJadeProvider.java`
- `src/main/java/com/example/examplemod/compat/jei/JEIIntegration.java`

### Configuracion
- `src/main/java/com/example/examplemod/config/ProductiveHoeConfig.java`
- `src/main/java/com/example/examplemod/config/ConfigEvents.java`
- Archivo generado por mundo: `saves/<mundo>/serverconfig/eduhzzepunks_productive_hoe-server.toml`

### Worldgen y loot
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/structure/abandoned_hut.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/structure/abandoned_garden.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/structure_set/abandoned_farm_set.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/template_pool/abandoned_hut/start_pool.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/template_pool/abandoned_garden/start_pool.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/structures/*.nbt`
- `src/main/resources/data/eduhzzepunks_productive_hoe/loot_tables/chests/abandoned_farming_cache.json`

### Localizacion y tags
- `src/main/resources/assets/eduhzzepunks_productive_hoe/lang/en_us.json`
- `src/main/resources/assets/eduhzzepunks_productive_hoe/lang/es_mx.json`
- `src/main/resources/assets/eduhzzepunks_productive_hoe/lang/es_es.json`
- `src/main/resources/data/minecraft/tags/blocks/replaceable_plants.json`

## 9) Build
```bash
./gradlew clean build -x test
```
Salida:
- `build/libs/eduhzzepunks_productive_hoe-<version>.jar`
