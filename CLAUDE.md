NeoForge 26.3.0.94, Minecraft 26.3. I'm coming from 1.20 and JDK 8. Explain new APIs and modern Java features as you use them. I copy code in myself, so give complete, clearly placed snippets.

## Project state
Port of the 1.20 Forge branch (`1.20`) is done for everything I want kept, and the mod now builds on MowLib.
- **Storage books** (`Blocks/Book/`): `grass_book` (8/page), `wool_book` (16/page), `leather_book` (32/page),
  `death_book` (64 pages x 64). One `BookBlock` + `BookBlockEntity`; `TintableBookBlock` (wool, leather) has a
  dyeable cover; `DeathBookBlock` is take-out only.
  - Placed: top-corner arrows turn pages (MowLib `PageArrows`); right-click stores on the open page; left-click takes
    1, sneak+left-click a stack; sneak+right-click with an empty hand picks it up with contents. Can't be mined.
    Thickness state 0-5 follows fullness. The open page's item spins above it (MowLib `SpinningItemRenderer`).
    MowLib's page keys turn it while looking at it; the Color Palette dyes the cover; the colour picker reads it.
  - Block entity: MowLib `PagedStorage` (pages) + `CBTintData` (cover), implements `IPageHolder` and
    `ISpinningItemHolder`.
  - Item (`BookItem`, a MowLib `IPagedStorageItem`): contents in `mowlib:paged_contents`. Bundle-style clicks,
    scroll / page keys to select a page, page-grid tooltip, `mowlib:paged_fullness` item model property.
  - Dyeing in crafting: MowLib's `mowlib:colorable_dye` (wool and leather books are in `#mowlib:dyeable_in_crafting`).
- **Death book** (`DeathBookHandler`, Bibliomania-only): on player death, places itself and stores all drops plus the
  player's head.
- **Crafting chain**: knife parts by knapping (`BibliomaniaKnapping` -> MowLib `KnappingRecipes`) -> `knife_stone`
  (MowLib `CraftingToolItem`, loses 1 durability per craft) -> fibers (grass: 20% from `#bibliomania:grass_fiber_plants`
  with a knife, `BibliomaniaBonusDrops` -> MowLib `BonusDrops`; wool: string/wool + knife) -> twisted thread -> weave
  -> books. Copper/iron/gold ingot in a book recipe = 12/16/24 pages.
- **Mob books** (`Blocks/MobBook/`, `Blocks/EssenceBook/`), shown by one `MobBookRenderer` (`IMobBookDisplay`):
  - **Bestiary** (`mob_book`, animals only): right-click an adult animal to store it (count + first mob's look via
    `MobAppearance`). Breed with food, kill with a sword (placed: left-click via `LeftClickBlock`; inventory: right-click),
    dye, shear (`IShearable`; mooshrooms are shear-killed via `shear_kills`), milk (`milkings`). Egg/scute drops via
    data maps `interval_drops` / `grow_up_drops`. Contents in `bibliomania:mob_contents` (`MobBookContents`).
  - **Book of Essence** (`essence_book`, non-animal mobs, respects `#mowlib:mob_display_blacklist`): set from a mob or
    spawner; kills/spawners fill essence; at 100% spawns mobs over time; nether star/heavy core boosts.
    Contents in `bibliomania:essence_contents` (`EssenceContents`).
  - Temporary mobs: `MobBookActions.createTemporary` (ID -1, never added to the world).
  - Timers and amounts: `ServerConfig` (`ModConfig.Type.SYNCED`, `<world>/syncedconfig/bibliomania-server.toml`).

## Known issues / to do
- Crafting a book that holds items into a death book deletes those items (same as 1.20).

## Ideas, not started
- Lecterns / shelves holding books, with hopper/pipe access (books themselves stay manual only). Plan to use MowLib's
  filters (`IFilterHolder`, the `mowlib:filter_*` items) and work cards (`IWorkCardHolder`, `mowlib:workcard_*`) there.
- More use of MowLib's custom dyes / Color Palette.

## MowLib (shared library)
- Depends on MowLib (mod id `mowlib`, package `com.mowmaster.mowlib`), bundled with Jar-in-Jar from mavenLocal
  (`com.mowmaster.mowlib:mowlib`, range `[0.0.2,1.0.0)`, prefer `0.0.2`). Source: `Mowmaster/MowLib`, branch
  `NeoForge-26x`. To update: publish MowLib (`gradlew publishToMavenLocal`), then raise `prefer` (and the ranges in
  build.gradle and neoforge.mods.toml if the new version is required).
- Before writing a shared system (tinting, storage, filters, transport, work cards, paging, knapping, bonus drops,
  tooltips, renderers, dyes...), clone MowLib and check whether it already exists. Use it; don't copy it into this mod.
  MowLib's CLAUDE.md lists what it contains.
- MowLib registers its own things for every mod: tint sources for every `IColorableBlock`, the paged tooltip, the
  `mowlib:paged_fullness` property, the page keys, knapping and bonus drop handlers. Don't register these here.
- Don't edit MowLib from this project's session. If something belongs in MowLib, write it up as
  `mowlib-from-bibliomania.zip` (MowLib files + notes + migration notes), as before.
- Old worlds: `Items/DataComponentRegistry` keeps the alias `bibliomania:book_contents` -> `mowlib:paged_contents`.
  Keep it (and its `register` call) as long as pre-MowLib worlds matter.

## 26.x notes (learned porting this mod)
- No NBT on items: data components. Block entities move them with `applyImplicitComponents` /
  `collectImplicitComponents`; recipe results set them with `"components"`.
- Block entities save with `ValueInput`/`ValueOutput`; removal side effects go in `preRemoveSideEffects`.
- Renderers: `extractRenderState` copies data into a render state, `submit` draws it.
- Item models live in `assets/<mod>/items/*.json` (`minecraft:range_dispatch`, `minecraft:dye` tints). Since 1.21.11
  block models can't use `textures/item/` textures (separate item atlas).
- Tools are plain `Item`s with `Item.Properties#tool(...)`; enchantability is item tags (`minecraft:enchantable/*`).
- 26.3 rewrote loot conditions, so simple drops are done with events (MowLib `BonusDrops`).
- Giving items to players: MowLib `StorageInteractions.giveToPlayer` (26.3's `placeItemBackInInventory` takes a
  `Prediction`; plain `Inventory#add` keeps leftovers when full, so items vanish).
- Recipe JSON: ingredients are plain ids or `#tags`; results use `"id"`.
- - Config types are `LOCAL` / `SYNCED` (no `SERVER`). `EntityType#is(tag)` and `EntityType.byString` are gone: use
    `BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(tag)` and `.getOptional(Identifier)`.
- Entities not added to a level have no ID; call `setId(-1)` (like spawner display mobs) before rendering or `die()`.

## Model conventions
- Texture slots `#texture_one/two/three`; `cullface` only on faces at the block edge; glow via
  `"light_emission": 15` + `"neoforge_data": {"ambient_occlusion": false}`. Tint region N = model `tintindex` N.

## Working with Claude
- Pull and read the repo only; never commit or push. I handle git. Send changed files as a zip laid out at their
  repo paths (I unzip into the repo root), based on my latest push, with a list of files to delete.
- To check 26.3 APIs, clone the public `neoforged/neoforge` repo (branch `26.3.x`) and read the porting primers in
  `neoforged/.github` (`primers/26.1`, `26.2`, `26.3`). NeoForge's docs are in `neoforged/Documentation` (`docs/`).
  NeoForge's test mods (`tests/`) and patch files show real 26.3 call sites. MowLib and CrystalBlocks compile on this
  version.
- The cloud environment can't download Minecraft, so you can't run Gradle: say which lines are unverified, and I'll
  compile and paste errors. Compiling this mod's Java together with MowLib's source with plain `javac` does catch
  calls to MowLib classes or methods that don't exist.
- For simple model fixes (a missing `tintindex`, `#`, texture key), just tell me the line to change; only send a model
  file when the geometry changes.
