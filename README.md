# Zyren Modded Content v2

Qi cultivation mod for Minecraft 1.21.1 (NeoForge 21.1.232, Java 21), built as an MCreator 2026.2 workspace.

- `src/main/java/net/mcreator/zyrenmoddedcontentv2/` — MCreator-generated code (items, effects, variables, etc.)
- `src/main/java/net/zyren/cultivation/` — hand-written cultivation system (aspects, sub-aspects, ability kits, Qi, corruption, death penalty, network, client UI)
- `elements/` + `zyrenmoddedcontent_v2.mcreator` — MCreator workspace definition

## Building
Open the folder as a workspace in MCreator 2026.2 and build, or run `./gradlew build`.

## Notes
- `PotionofCRBrewingRecipe.java` (MCreator-generated) is not in this upload; MCreator regenerates it on build.
- Design docs live in the claude.ai project ("minecraft mod for 1.21.1 neoforge 21.1.232").
