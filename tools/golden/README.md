# Golden saves (release gate G13)

After every release, add saves written by **that release's own engine** to
`core/src/test/resources/saves/golden/<version>/`. `ReleaseSaveCompatibilityTest` then checks that every past
version's saves keep playing in the current engine.

How (about 5 minutes, JVM only, no emulator):

1. `git worktree add <tmp>/wt v<version>` and copy `local.properties` into it.
2. In the worktree, copy the app's debug scenarios into a core test:
   `sed 's/^internal object DebugScenarios/object DebugScenariosCopy/' app/src/main/kotlin/com/arnavgames/tycoon/game/DebugScenarios.kt > core/src/test/kotlin/com/arnavgames/tycoon/game/DebugScenariosCopy.kt`
3. Copy `GoldenSaveGen.kt.txt` from this folder to `core/src/test/kotlin/com/arnavgames/tycoon/game/GoldenSaveGen.kt`.
4. Run `ABT_GOLDEN_OUT=<out>/<version> ./gradlew :core:test --tests com.arnavgames.tycoon.game.GoldenSaveGen -i`.
5. Copy `<out>/<version>/*.abtsave` into the main repo's golden folder, then `git worktree remove --force <tmp>/wt`
   (on Windows, delete leftover build folders with a `\?\` long path if needed).
