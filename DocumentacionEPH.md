# Eduzzepunks Productive Hoe - Documentacion Tecnica

## 1) Resumen
Mod para Forge 1.20.1 orientado a agricultura y exploracion ligera:
- Cosecha manual inteligente con azadas.
- Encantamientos propios (`Acreage`, `Bountiful Seed`).
- Limpieza de maleza por area con click derecho.
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
- Nivel III: **no** (bloqueado subiendo el costo de nivel III a rango imposible).

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
  - ES: `Semilla Pródiga`

#### Obtencion por mesa de encantamientos
- Nivel I y II: si.
- Nivel III: **no** (bloqueado en mesa).

#### Efecto A: no consumir semilla/cultivo de replantado
Probabilidad por cultivo cosechado:
- Nivel I: 15%
- Nivel II: 25%
- Nivel III: 40%

Aplica tambien a cultivos donde semilla y producto son el mismo item (zanahoria/papa).

#### Efecto B: bonus estilo fortuna para cultivos
- Aplica bonus tipo fortuna al drop de cultivo.
- Excluye semillas cuando son item separado.
- En cultivos de item unico (zanahoria/papa), el bonus se aplica al rendimiento util.

## 3) Cosecha y durabilidad

### 3.1 Cosecha de cultivos
- Click derecho con azada sobre cultivo maduro:
  - cosecha,
  - drops vanilla,
  - replantado automatico a edad 0.

### 3.2 Durabilidad de cosecha de cultivos
Ya no es fija por cultivo.

Por cada cultivo cosechado:
- 30% de probabilidad de consumir 1 punto de durabilidad.
- 70% de no consumir.

## 4) Limpieza de maleza con azada (nuevo)

Se activa con click derecho sobre bloques del tag:
- `#minecraft:replaceable_plants`

### 4.1 Direccion
El area se orienta usando `player.getDirection()` (horizontal):
- si mira al norte, limpia hacia norte y se abre hacia este/oeste.
- equivalente para sur/este/oeste.

### 4.2 Area base por tier
Se calcula con `baseWidth` y `baseDepth` por material:
- Madera: `3 x 3`
- Piedra: `5 x 4`
- Hierro: `7 x 5`
- Diamante: `9 x 6`
- Netherite: `11 x 7`

Si la azada tiene `Acreage`:
- `+2` al ancho por nivel.
- `+2` al largo por nivel.

### 4.3 Ejecucion
- Recorre offsets `x,y,z` relativos al bloque clicado.
- Si el bloque pertenece a `replaceable_plants`, se elimina con `world.destroyBlock(pos, false)`.
- Al limpiar correctamente, se dispara feedback de **barrido** (animacion sweep + particulas sweep + sonido).

### 4.4 Durabilidad de limpieza
- 1 punto de durabilidad por cada 5 bloques eliminados.
- Formula aplicada: `ceil(eliminados / 5)`.

## 5) Estructuras de mundo (Jigsaw)

## 5.1 Tipo y distribucion
Se agregan dos estructuras de superficie (`surface_structures`) usando jigsaw/template pools:
- `abandoned_hut`
- `abandoned_garden`

Cada categoria ahora tiene variantes aleatorias:
- Cabanas: `abandoned_hut_1`, `abandoned_hut_2`, `abandoned_hut_3`
- Jardines: `abandoned_garden_1`, `abandoned_garden_2`, `abandoned_garden_3`

El pool jigsaw selecciona una variante por peso (actualmente balanceadas), por lo que no aparece siempre el mismo diseño.
Las cabañas incluyen decoracion interior (cama, zona de trabajo, almacen y luz), ademas del barril de loot.
Se corrigio la plantilla para evitar coordenadas fuera de rango del NBT (causa comun de que una estructura no aparezca).

Biomas permitidos:
- Plains
- Forest
- Swamp

Rareza configurada con `random_spread` estilo frecuente-moderada (similar a estructuras de superficie poco comunes):
- spacing: 32
- separation: 8

## 5.2 Sistema de loot (loot only)
Ambas estructuras tienen barril con loot table personalizada:
- `eduhzzepunks_productive_hoe:chests/abandoned_farming_cache`

Contenido:
1. 100% de probabilidad de **1 libro encantado**.
2. Ese libro tiene **solo uno** de estos encantamientos:
   - `acreage` III
   - `bountiful_seed` III
3. El resto del loot se rellena con comida aleatoria (pan, zanahorias, papas, manzanas, estofados).

## 6) Archivos principales

### Java
- `src/main/java/com/example/examplemod/ProductiveHoeMod.java`
- `src/main/java/com/example/examplemod/event/FarmingEventHandler.java`
- `src/main/java/com/example/examplemod/util/HarvestLogicUtil.java`
- `src/main/java/com/example/examplemod/util/PlantCleanupUtil.java`
- `src/main/java/com/example/examplemod/enchant/AcreageEnchantment.java`
- `src/main/java/com/example/examplemod/enchant/BountifulSeedEnchantment.java`
- `src/main/java/com/example/examplemod/enchant/ModEnchantments.java`

### Worldgen / Loot
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/structure/abandoned_hut.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/structure/abandoned_garden.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/structure_set/abandoned_farm_set.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/template_pool/abandoned_hut/start_pool.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/worldgen/template_pool/abandoned_garden/start_pool.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/structures/abandoned_hut.nbt`
- `src/main/resources/data/eduhzzepunks_productive_hoe/structures/abandoned_garden.nbt`
- `src/main/resources/data/eduhzzepunks_productive_hoe/loot_tables/chests/abandoned_farming_cache.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/tags/worldgen/biome/has_structure/abandoned_hut.json`
- `src/main/resources/data/eduhzzepunks_productive_hoe/tags/worldgen/biome/has_structure/abandoned_garden.json`

### Localizacion
- `src/main/resources/assets/eduhzzepunks_productive_hoe/lang/en_us.json`
- `src/main/resources/assets/eduhzzepunks_productive_hoe/lang/es_mx.json`
- `src/main/resources/assets/eduhzzepunks_productive_hoe/lang/es_es.json`

## 7) Build
```bash
./gradlew clean build -x test
```
