# HWELL Client — Fabric standalone framework

Target: Minecraft 1.21.11, Java 21, Fabric Loader + Fabric API. This project removes the Meteor Client dependency from the active Gradle build and adds a custom HWELL ClickGUI (Right Shift).

## Important migration status

This is a **standalone client foundation**, not a completed port of every old addon feature yet. The original Java sources, mixins, HUDs, and module implementations are preserved unchanged under `migration-reference/original-src/` so none of the original logic is discarded. They are deliberately outside `src/main/java`, because those classes import Meteor APIs and cannot compile or run independently until those API calls are replaced.

Currently implemented in the standalone runtime:
- Fabric client entrypoint and keybind to open ClickGUI with Right Shift.
- Small module lifecycle/manager (`ClientModule`, `ModuleManager`).
- Custom HWELL-themed ClickGUI with toggleable module rows.
- Example module to demonstrate the framework.

Still needs porting to achieve full feature parity:
- AutoTotemSlot, AutoTotemProMax, AutoFillTotem, AutoCrystal and their settings/events/inventory helpers.
- AutoBuyShop variants, silent shop/movement mixins, and auto-enable listener.
- ArmorHud and InventoryHud.
- Meteor command and friends/settings integrations.

No existing feature logic has been deleted; it remains in the migration reference. It is not accurate to claim the original features already work in this independent build.

## Build on Windows

1. Install a 64-bit Java 21 JDK and make sure `java -version` reports 21.
2. Extract this folder to a normal writable path.
3. Open Command Prompt/PowerShell in this folder.
4. Run:

   ```bat
   .\gradlew.bat build
   ```

5. The mod jar should appear in `build\libs\hwell-client-0.1.0.jar`.
6. Install Fabric Loader for Minecraft 1.21.11 and put the jar plus Fabric API for 1.21.11 into `.minecraft\mods`.
7. Launch the Fabric 1.21.11 profile and press Right Shift to open ClickGUI.

First build needs internet to download Gradle, Minecraft mappings, Fabric Loader and Fabric API. If Gradle reports that the Fabric API version cannot be resolved, update `fabric_version` in `gradle.properties` to a published Fabric API release for Minecraft 1.21.11.

## Project layout

- `src/main/java/com/hwell/client/` — independent client bootstrap, module framework and ClickGUI.
- `src/main/resources/fabric.mod.json` — Fabric mod metadata; no Meteor dependency.
- `migration-reference/original-src/` — unchanged original addon sources and resources for systematic migration.
