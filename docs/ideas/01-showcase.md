# 01 — Showcase (Shift+X share, JEI click-through)

Goal: Showcaser-style sharing on 1.12.2. Hover anything → Shift+X → chat line others can hover (tooltip) and click (opens JEI recipes/uses on **their** client).

---

## 1. Reference

### Prior art
- **Showcaser** (modern): Shift+X on slots / JEI / REI / EMI / quests; clickable recipe messages.
- **Ezshow** (1.12.2): `/show` held item, server-only, cooldown + PermissionAPI nodes.
- **Show Everything** (modern): keybind from any GUI incl. JEI; sends shortened hover to clients when NBT is too large.

### APIs
| Need | API (1.12.2) |
|---|---|
| Keybind with Shift | `new KeyBinding(key, KeyConflictContext.GUI, KeyModifier.SHIFT, Keyboard.KEY_X, cat)` |
| Key press inside GUIs | `GuiScreenEvent.KeyboardInputEvent.Pre` + `kb.isActiveAndMatches(Keyboard.getEventKey())` |
| Container slot under mouse | `GuiContainer#getSlotUnderMouse()` |
| JEI runtime | `@JEIPlugin` `IModPlugin#onRuntimeAvailable(IJeiRuntime)` |
| JEI list overlay | `IIngredientListOverlay#getIngredientUnderMouse()`, `#hasKeyboardFocus()` |
| JEI bookmarks | `IBookmarkOverlay#getIngredientUnderMouse()` ⚠ verify present in pack's JEI **4.16.1.1012** |
| JEI recipe screen | `IRecipesGui#getIngredientUnderMouse(IIngredientType)` ⚠ verify; fallback: reflect on JEI's focused recipe layout |
| Open JEI | `IRecipesGui#show(IRecipeRegistry#createFocus(IFocus.Mode.OUTPUT/INPUT, stack))` |
| Client command | `ClientCommandHandler.instance.registerCommand(...)` — chat `RUN_COMMAND` clicks route through it locally |
| Hover | `HoverEvent(SHOW_ITEM, new TextComponentString(stack.writeToNBT(new NBTTagCompound()).toString()))` |
| Fluid | JEI ingredient `FluidStack` → hover `SHOW_TEXT` (name + amount + mod) |

---

## 2. Spec

### 2.1 Sources (client)
Priority when Shift+X pressed:
1. JEI recipe screen ingredient under mouse
2. JEI ingredient list / bookmark overlay
3. `GuiContainer` slot under mouse (inventory, chests, Baubles, mod GUIs)
4. No GUI open → main hand, then off hand

Skip if a text field has focus (`hasKeyboardFocus()` or `GuiTextField` focused) so typing "X" in JEI search never shares.

### 2.2 Share kinds
| kind | payload | message |
|---|---|---|
| ITEM | full stack NBT | `<Name> [Inv] [Item ×n]` |
| FLUID | fluid registry name + amount | `<Name> [JEI] [Fluid 1000mB]` |
| RECIPE_LINK | ITEM + jeiMode (RECIPES/USES) | same line, click opens JEI |

`source` tag: `INV` (from player's real inventory) or `JEI` (catalog). Every item line is clickable; default click = RECIPES, Ctrl+Shift+X = USES.

### 2.3 Server rules
- Cooldown `share.cooldownSec = 3` per player.
- `source=INV` → server verifies the player actually holds a matching stack (`ItemStack.areItemStacksEqual` across inventory + Baubles via reflection soft-dep). Mismatch ⇒ re-tag as `JEI`.
- **NBT budget**: serialize component JSON; if > `share.maxJson = 30000`, strip NBT to `{id, Count, Damage, display.Name, ench}` and append `(tooltip trimmed)`.
- Broadcast scope: `share.scope = GLOBAL | DIMENSION | RADIUS(n)`; default GLOBAL.
- Ban list: `share.blockedItems` (registry names).

### 2.4 Message format
```
<Name> [Inv] [Lightning Dragonbone Longsword]
         │      └─ hover: SHOW_ITEM (full NBT)   click: /wb jei recipes iceandfire:dragonbone_sword_lightning 0
         └─ hover: "From inventory" / "From JEI"
```
Item name colored by rarity; brackets gray.

### 2.5 Client without jar
Sees hover normally. Click sends `/wb jei …` to server → server command `wb` replies "Install Warband to open recipes."

### 2.6 Config
`enabled, cooldownSec, maxJson, scope, radius, blockedItems, allowJeiSource, allowFluid`.

### 2.7 Future
- `LOADOUT`: armor + baubles + hands in one message (each hoverable).
- `WAYPOINT`: shared coord, click → creates local ping (02).

---

## 3. Implementation plan

```
share/
  ShareModule.java          registers handlers
  client/ShareKeys.java     KeyBinding + KeyboardInputEvent.Pre
  client/HoverCapture.java  source priority chain, returns CaptureResult{kind,source,stack,fluid}
  client/JeiCompat.java     @JEIPlugin, runtime holder, getUnderMouse(), open(stack, mode)  [loaded only if JEI present]
  client/CmdJei.java        IClientCommand "/wb jei"
  net/ShareReq.java         IMessage + handler
  ShareService.java         validation, budget trim, component builder, broadcast
  CmdDctServer.java         server "/wb" fallback reply
```

### Steps
1. **Keybind + held-item path** (no GUI). ShareReq → ShareService → broadcast. Verify hover on a client *without* jar.
2. **GuiContainer slot capture.** Test player inventory, chest, Baubles screen.
3. **JeiCompat** (guard with `Loader.isModLoaded("jei")`, keep JEI classes isolated so missing JEI never class-loads them). List overlay → bookmark → recipe screen.
4. **Client command + click event** → JEI opens on receiver.
5. **INV verification** on server, re-tag on mismatch.
6. **NBT budget**: craft a test item with huge NBT (`/give` with long lore) → confirm trim, no kick.
7. Fluid kind.
8. Config + lang.

### Tests
- Shift+X while typing in JEI search → no share.
- Spam → cooldown message.
- Modded item with capability NBT (Ice and Fire gear, reforged baubles) → tooltip matches original.
- Receiver without JEI installed → click shows "JEI not installed".
