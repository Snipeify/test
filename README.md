# CartAssist (Fabric 1.21.4)

If your IDE shows errors like:
- `Cannot resolve symbol 'MinecraftClient'`
- `Cannot resolve symbol 'ClientTickEvents'`
- `Class 'CartAssistClient' is never used`

it almost always means **Gradle sync/import failed**, so Minecraft/Fabric dependencies were never downloaded.

## Required versions
- **JDK 21** (exactly; do not use Java 25 for this project)
- Gradle via your IDE import (or wrapper when present)

## IntelliJ fix steps
1. Install JDK 21.
2. Open the project as a **Gradle** project (not plain folder).
3. Set Gradle JVM to JDK 21:
   - `Settings -> Build, Execution, Deployment -> Build Tools -> Gradle -> Gradle JVM = 21`
4. Click **Reload All Gradle Projects**.
5. If still broken: `File -> Invalidate Caches / Restart`.

## VS Code fix steps
1. Install JDK 21.
2. Install Java Extension Pack + Gradle for Java.
3. Set `java.jdt.ls.java.home` to JDK 21.
4. Run `Java: Clean Java Language Server Workspace`.
5. Reimport Gradle project.

## Why this happens
Fabric classes (`MinecraftClient`, `ClientTickEvents`, etc.) come from Gradle dependencies.
If Gradle cannot run/sync (wrong JDK/runtime), your IDE treats those imports as missing.

## Entry point
- Main class: `org.optimizer.cartassist.client.CartAssistClient`
- Registered in `src/main/resources/fabric.mod.json`
