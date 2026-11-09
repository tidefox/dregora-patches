# Client Quirks — handoff for a new client-side repo

> **Status:** spec only (2026-11-09). Not built here: `dregora-patches` is server-only (CLAUDE.md hard rule 2).
> Copy this file into the new repo as `docs/spec.md` and delete it here once that repo exists.
> Working name **Client Quirks**; rename freely.

## 0. Scope
- **Target:** Minecraft 1.12.2, Forge 14.23.5.2860, Java 8 language level. **Client only** (`clientSideOnly = true`, `acceptableRemoteVersions = "*"`). Servers never need it; players without it are unaffected.
- **Universal:** no dependency on Dregora or any pack mod. It works on macOS, Windows and Linux. Each fix has its own toggle, and Mac-only fixes do nothing elsewhere (`Minecraft.IS_RUNNING_ON_MAC` / `Util.getOSType()`).
- **Not in scope:** Retina/HiDPI and fullscreen. The owner already has a private Retina fix jar and reports no Retina problem. That jar must still be checked for overlapping mixins (§3 step 1).
- Later ports to other versions are out of scope until the 1.12.2 build is done.

| | |
|---|---|
| Repo | owner creates it (e.g. `tidefox/client-quirks`) |
| Mod | mod ID `clientquirks`, package `io.github.tidefox.clientquirks` |
| Build | copy `tidefox/skinbridge` / this repo: RetroFuturaGradle 2.0.3, wrapper 9.7 (Gradle on Java 25), Java 8 toolchain, MCP `stable_39`. FermiumBooter coremod, mixins under `mixin/` with the `cq$` prefix. **Early** (vanilla) mixin config with refmap, `"required": false`, `defaultRequire: 0`. MixinBooter is compile-only, never shipped |
| Config | Forge `@Config`, one category per fix, all toggles `true` by default except where noted; client config GUI via `@Config` + `ConfigManager` |
| CI | build + unit tests; boot a Forge 2860 dedicated server with the jar to prove it stays harmless there (`clientSideOnly`). A real client can't boot headless in CI, so checking that the mixins apply is manual: `./gradlew runClient` with `-Dmixin.debug.verify=true` |
| Prior art | **MacOS Input Fixes** (hamarb123, BSD-3, Fabric 1.14+ only, `github.com/hamarb123/MCMacOSInputFixes`): same Ctrl/Cmd drop fix, Ctrl+click fix, trackpad scroll fixes. Read it for ideas; keep its notice if any code is borrowed |

## 1. Release 1 — the owner's three issues (+ stuck modifier)

### 1.1 Ctrl+Q doesn't drop the stack (macOS)
**Cause:** `GuiScreen.isCtrlKeyDown()` checks **Cmd** (`KEY_LMETA` 219 / `KEY_RMETA` 220) on Mac, Ctrl (29 / 157) elsewhere. So "drop stack" on Mac is Cmd+Q, and Cmd+Q also quits the app.

**Fix:** in the two drop paths only, treat physical Ctrl as Ctrl on Mac as well:
- `Minecraft.processKeyBinds`: `player.dropItem(GuiScreen.isCtrlKeyDown())` (in-world drop).
- `GuiContainer.keyTyped` → drop-key branch: `handleMouseClick(slot, id, isCtrlKeyDown() ? 1 : 0, ClickType.THROW)` (drop from the hovered slot).

`@WrapOperation` on those `isCtrlKeyDown()` calls → `original || (mac && (Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157)))`. Don't change `isCtrlKeyDown()` globally, because Cmd+C/V/A in text fields depend on it. Cmd+Q keeps dropping the stack while §1.3 is on (§1.3 eats the close request).

⚠ verify: LWJGL2 reports `KEY_LCONTROL` on Mac while Q is held (the Ctrl+click remap is mouse-only, so it should). Also verify exact call sites against the decompiled 1.12.2 sources, and check Forge `KeyModifier.CONTROL` (also Cmd on Mac) when Drop is bound with a modifier through Controlling.

### 1.2 Sound stays on the old output device until restart (all OSes, worst on Mac)
**Cause:** paulscode/LWJGL2 OpenAL opens the default device once (`AL.create()`) at sound-system init and never re-checks. AirPods/headphones/HDMI switches → sound stays on the old device or goes silent. Restart works because init reopens the *current* default.

**Fix:** reload only the sound system (≈ 1 s), never a full resource reload (F3+T takes minutes in RLCraft):
- `SoundHandler.sndManager` (private) → accessor mixin; call `SoundManager.reloadSoundSystem()` (= unload + load) on the main thread (`Minecraft.addScheduledTask`). Music restarts on its own via `MusicTicker`. ⚠ verify the method name in `stable_39`.
- **Manual:** keybind "Reload sound device" (unbound by default) + a button on the Music & Sounds screen (`GuiScreenEvent.InitGuiEvent.Post` on `GuiScreenOptionsSounds`). Toast or chat line "Sound device reloaded".
- **Automatic** (`audio.autoSwitch`, on): a daemon thread polls every 2 s and schedules a reload when the default output changes:
  - **macOS:** CoreAudio via JNA (`AudioObjectGetPropertyData`, `kAudioObjectSystemObject` / `kAudioHardwarePropertyDefaultOutputDevice` → `AudioDeviceID`). JNA ships with 1.12.2 (`net.java.dev.jna:jna` 4.4.0, used by the narrator). ⚠ verify it's on the classpath in the Dregora launcher.
  - **Windows/Linux:** `ALC10.alcGetString(null, ALC_DEFAULT_DEVICE_SPECIFIER)` change, plus `ALC_EXT_disconnect` → `ALC_CONNECTED == 0` on the open device. ⚠ verify the OpenAL Soft bundled with LWJGL 2.9.4 refreshes the default without re-init; if not, Windows/Linux get manual reload only, and the docs say so.
  - Debounce: wait until the new device is stable for 1 s; never reload more than once per 5 s; skip while the game is still loading.

### 1.3 Cmd+Q quits the game (macOS; optional elsewhere)
**Cause:** LWJGL2's Cocoa app turns the Quit menu item (Cmd+Q) into a close request. `Minecraft.runGameLoop` does `if (Display.isCreated() && Display.isCloseRequested()) shutdown();`.

**Fix:** wrap that `Display.isCloseRequested()` call. If it's true **and** a world is loaded **and** `quit.confirm` is on → return false and open a confirm screen (`GuiYesNo`: "Quit Minecraft? / Your world is saved on quit"). "Quit" → `mc.shutdown()` (normal path: unloads and saves the world). A second Cmd+Q while the screen is open = quit. In menus with no world loaded, quit as normal.
- The red window button sends the same request, so it gets the same prompt. That also helps Windows/Linux (`quit.confirmEverywhere`, default off).
- ⚠ verify: the LWJGL2 Mac `isCloseRequested()` clears the flag on read (otherwise it re-fires every frame), and whether Cmd+Q and the red button can be told apart (if yes: `quit.blockOnlyCmdQ` option).

### 1.4 Stuck Cmd/Ctrl/movement keys after Cmd+Tab / Alt+Tab (all, worst on Mac)
**Symptom:** the key-up event is lost while the window is unfocused, so the game thinks Cmd/Ctrl/W is still held. Plain Q then drops whole stacks, and the player keeps walking.
**Fix:** on focus regained (`Display.isActive()` edge, client tick), re-sync: `KeyBinding.unPressAllKeys()` + `KeyBinding.updateKeyBindState()`; modifier checks read `Keyboard.isKeyDown`, so verify LWJGL2 itself also has the stale state. If it does, the drop fix (§1.1) must also ignore modifiers whose down-event came before the focus loss. ⚠ verify on the owner's Mac before writing code: Cmd+Tab out while holding nothing, come back, press Q.
Ships in release 1 because a stuck Cmd breaks §1.1.

## 2. Later releases (verify each is real first)
| # | Problem | Platform | Fix sketch |
|---|---|---|---|
| 5 | Ctrl+left click becomes right click | Mac | mouse event wrapper: Ctrl held + button 1 from a physical left click → button 0 (toggle) |
| 6 | Trackpad / Magic Mouse scroll jumps several hotbar slots, momentum keeps scrolling | Mac mostly | add up `Mouse.getEventDWheel()` until a threshold is reached, "one slot per notch" (discrete) option, sensitivity, time-based momentum filter. Check Mouse Tweaks' inventory scrolling for conflicts |
| 7 | Ctrl/Cmd+B toggles the narrator by accident | all | option to disable the hotkey |
| 8 | Movement key still held after closing chat/GUI | all | re-sync key states on GUI close (shares code with §1.4) |
| 9 | Sounds cut out in big fights ("Maximum sound pool size … reached") | all | option for more normal-channel slots (`SoundSystemConfig.setNumberNormalChannels`) |

## 3. Order of work
1. **Conflict check:** read the client pack's `mcmouser-1.12.2-forge-1.0.3.jar`, `MouseTweaks-2.10.1`, `Controlling-3.0.12.4` and the owner's Retina jar. List every class and method they patch (`Minecraft.runGameLoop`, `processKeyBinds`, `GuiContainer`, `MouseHelper`, `SoundManager`), and drop or adapt any fix that would patch the same place twice.
2. Repo + build + CI skeleton (§0).
3. Release 1: §1.1–1.4, each behind its toggle.
4. Owner tests release 1 on the Mac in the Dregora instance (Ctrl+Q in world and in inventory, Cmd+C/V in chat still work, switch AirPods ↔ speakers with music playing, Cmd+Q in a world and in the main menu, Cmd+Tab with W held).
5. Release 2: §2 items that the owner confirms are real.

## 4. Open questions
- Repo name/creation (owner).
- The Retina jar: upload it for step 1, or list what it patches.
- Audio: wired or Bluetooth, and does the game go silent or keep playing on the old device? (It decides whether `ALC_CONNECTED` alone would catch it.)
