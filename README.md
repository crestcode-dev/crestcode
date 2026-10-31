# CrestCode
<div align="center"> **A Desktop-Grade Mobile 
IDE for Android** *Powered by Jetpack Compose, 
Monaco Editor, and a PRoot Alpine Linux Sandbox* 
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(SDK%2026--34)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#) 
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](#) 
[![Editor](https://img.shields.io/badge/Editor-Monaco%20Editor%20(VS%20Code)-007ACC?style=for-the-badge&logo=visualstudiocode&logoColor=white)](#) 
[![Sandbox](https://img.shields.io/badge/Linux%20Sandbox-Alpine%20%2B%20PRoot-0D597F?style=for-the-badge&logo=alpinelinux&logoColor=white)](#) 
[![License](https://img.shields.io/badge/License-MIT-brightgreen?style=for-the-badge)](#) 
</div> ---
## Overview
**CrestCode** is a next-generation mobile 
Integrated Development Environment (IDE) 
engineered specifically for Android devices. 
Traditional mobile code editors often compromise 
by offering basic text areas or fragile web 
wrappers. CrestCode bridges the gap between 
phone mobility and desktop software development 
by combining: 1. **Desktop Monaco Editor** (the 
exact editor powering Visual Studio Code) 
compiled and fine-tuned for high-performance 
touch interaction. 2. **Alpine Linux Sandbox** 
running inside user space via PRoot, providing a 
full GNU/Linux toolchain directly on your phone 
without requiring root privileges. 3. **Jetpack 
Compose Native Shell** delivering a fluid 60/120 
FPS interface with multi-tab management, sidebar 
workspace explorer, extra developer key toolbar, 
and a VS Code-inspired settings engine. ---
## System Architecture
``` 
┌────────────────────────────────────────────────────────────────────────┐ 
│ CrestCode Android Shell │ │ │ │ 
┌────────────────────────────────────────────────────────────────┐ 
│ │ │ Jetpack Compose UI │ │ │ │ • Top Bar (Undo 
/ Redo / Run / Settings) │ │ │ │ • Multi-Tab 
Workspace Bar (Dirty Indicators •) │ │ │ │ • 
File Drawer (File tree, new files, new folders) 
│ │ │ │ • Extra Keys Toolbar (Ctrl, Alt, Tab, 
Esc, Arrows) │ │ │ │ • Settings Screen (VS Code 
layout + Raw settings.json view) │ │ │ 
└───────────────────────────────┬────────────────────────────────┘ 
│ │ │ │ │ 
┌────────────────────────┴────────────────────────┐ 
│ │ ▼ ▼ │ │ ┌───────────────────────────────┐ 
┌──────────────────────────────┐ │ │ │ Monaco 
Editor Core │ │ Alpine Linux Runtime │ │ │ │ 
(Vite + TypeScript Web) │ │ (PRoot + Terminal 
View) │ │ │ │ • 70+ Language Syntaxes │ │ • Full 
Linux userland │ │ │ │ • Custom Mobile Touch 
Handles │ │ • CPM Package Manager │ │ │ │ • 
Direct OS Clipboard │ │ • Python, Node.js, 
C/C++, Git│ │ │ │ • VS Code Auto-Save Engine │ │ 
• Termux-based emulator view │ │ │ 
└──────────────┬────────────────┘ 
└──────────────┬───────────────┘ │ │ │ │ │ │ 
└────────────────┬────────────────┘ │ │ ▼ │ │ 
┌────────────────────────────────────────────────────────────────┐ 
│ │ │ Core Management Layer │ │ │ │ • 
MonacoBridge (Two-way JS <-> Kotlin IPC) │ │ │ │ 
• SettingsManager (JSON-backed reactive state 
engine) │ │ │ │ • WorkspaceManager (Safe disk 
I/O, file operations) │ │ │ │ • 
TerminalSessionManager (PTY lifecycle & process 
bridge) │ │ │ 
└────────────────────────────────────────────────────────────────┘ 
│ 
└────────────────────────────────────────────────────────────────────────┘ 
``` ---
## Key Features
### 1. Desktop Monaco on Touch Devices
- **In-Tree Vite Build:** Monaco Editor core is 
compiled and bundled directly inside the 
application assets for instantaneous offline 
loading. - **Precision Touch Handles:** 
Custom-crafted touch selection anchors that 
prevent finger occlusion and deliver 1-touch 
grab-and-drag word expansion. - **Floating 
Action Toolbar:** Cut, Copy, Paste, and 
Select-All floating HUD positioned dynamically 
above active text selections. - **Direct System 
Clipboard:** Seamless copy and paste 
communicating directly with Android's 
`ClipboardManager` without intrusive browser 
permission dialogs. - **Keyboard Stability:** 
Hardened touch and IME scroll-guards preventing 
virtual keyboards (Gboard, Samsung Keyboard) 
from randomly dismissing during backspace, 
enter, or scroll gestures.
### 2. VS Code-Style Auto-Save Engine
Implements the exact multi-mode auto-save logic 
found in Microsoft Visual Studio Code: - 
**`afterDelay`:** Intelligent debouncing — 
avoids hammering flash storage on every 
keystroke by queueing saves until the user 
pauses typing (default: 1000ms). - 
**`onWindowChange`:** Automatically commits all 
dirty buffers the millisecond CrestCode loses 
focus or the user switches apps (`Android 
onPause / visibilitychange`). - 
**`onFocusChange`:** Flushes pending edits when 
navigating between tabs, terminal, or settings. 
- **`off`:** Pure manual save mode (`Ctrl+S`). - 
**Dirty State Tracking:** Real-time dirty dot 
(`•`) indicator in tab headers that clears 
dynamically upon successful disk write.
### 3. AI-Ready Settings Manager & VS Code UI
- **Unified `settings.json`:** Settings are 
stored in a human-readable, script-accessible 
JSON configuration file 
(`context.filesDir/settings.json`). Future AI 
agents, local shell scripts, and plugins can 
inspect and alter editor behavior 
programmatically. - **Reactive Hot-Reloading:** 
Any modification to settings instantly 
propagates across the Monaco engine and the UI 
without requiring an application restart. - **VS 
Code Settings UI:**
  - Real-time search filter for discovering 
  settings. - Category filter pills (*Commonly 
  Used, Files, Text Editor, Terminal, 
  Appearance, Modified*). - Blue accent modified 
  indicators matching VS Code desktop 
  aesthetics. - **Raw JSON Switcher:** 1-tap 
  toggle (`{ }` icon) to edit raw 
  `settings.json` directly with built-in JSON 
  validation and formatting.
### 4. Alpine Linux Sandbox & Terminal
- **PRoot Architecture:** Executes an isolated 
64-bit Alpine Linux distribution within Android 
app storage — **no root required**. - **CPM 
(Crest Package Manager):** Pre-configured 
package installer delivering programming 
runtimes (Python, Node.js, C/C++, Rust, Go) and 
dev utilities directly from the Crest-Packages 
repository. - **Native Terminal View:** Powered 
by the proven Termux terminal emulator engine 
with full ANSI color support, mouse tracking, 
and custom extra keys (Ctrl, Alt, Tab, Esc, 
direction arrows). ---
## Project Structure
``` crestcode/ ├── app/ │ ├── src/main/ │ │ ├── 
AndroidManifest.xml │ │ ├── assets/ │ │ │ ├── 
alpine/ # RootFS bootstrapping & setup scripts │ 
│ │ └── editor/ # Production Monaco bundle 
(assets/index.js) │ │ ├── java/com/crestcode/ │ 
│ │ ├── MainActivity.kt # Primary Compose 
Scaffold & Navigation │ │ │ ├── core/ │ │ │ │ 
├── filesystem/ # Storage & SAF handlers │ │ │ │ 
└── settings/ # SettingsManager (Schema & JSON 
engine) │ │ │ ├── editor/ │ │ │ │ └── 
MonacoBridge.kt # JavaScript Interface IPC 
bridge │ │ │ ├── runtime/ # AlpineManager, PRoot 
& TerminalSession │ │ │ ├── ui/ │ │ │ │ ├── 
MainViewModel.kt # Unidirectional UI state 
management │ │ │ │ ├── SettingsScreen.kt # VS 
Code-style preferences screen │ │ │ │ ├── 
TerminalScreen.kt # Integrated Linux terminal UI 
│ │ │ │ ├── components/ # Tabs, Drawers, 
TopBars, ExtraKeys │ │ │ │ └── theme/ # Color 
palettes, typography & styling │ │ │ └── 
workspace/ # File tree & workspace manager │ │ 
└── jniLibs/arm64-v8a/ # Native PRoot & Termux 
binaries ├── editor-core/ # Standalone Vite 
project for Monaco │ ├── src/ │ │ ├── main.js # 
Monaco initialization & touch handlers │ │ ├── 
styles.css # Selection handles & floating 
toolbar │ │ └── core/ # In-tree VS Code Monaco 
modules │ └── vite.config.js # Builds directly 
into app/src/main/assets/ └── build.gradle.kts 
``` ---
## Configuration Reference (`settings.json`)
| Setting Key | Type | Default | Description |
| :--- | :---: | :---: | :--- |
| `files.autoSave` | `string` | `"afterDelay"` | 
| Auto-save mode: `off`, `afterDelay`, 
| `onFocusChange`, `onWindowChange` | 
| `files.autoSaveDelay` | `number` | `1000` | 
| Delay in ms before saving dirty buffer in 
| `afterDelay` mode | `files.showHiddenFiles` | 
| `boolean` | `false` | Displays dotfiles in the 
| sidebar workspace explorer | `editor.fontSize` 
| | `number` | `14` | Editor font size in pixels 
| |
| `editor.tabSize` | `number` | `4` | Number of 
| spaces represented by a tab | 
| `editor.wordWrap` | `string` | `"on"` | Line 
| wrapping: `off`, `on`, `wordWrapColumn`, 
| `bounded` | `editor.minimap.enabled`| 
| `boolean` | `false` | Toggles the code 
| overview minimap | `editor.cursorBlinking` | 
| `string` | `"smooth"` | Cursor animation: 
| `smooth`, `blink`, `phase`, `expand`, `solid` 
| |
| `editor.theme` | `string` | `"vs-dark"` | 
| Color theme: `vs-dark`, `vs`, `hc-black` | 
| `terminal.integrated.fontSize` | `number` | 
| `13` | Terminal screen font size in pixels |
---
## Build & Development
### Requirements
- **Android SDK:** Compile SDK 34, Min SDK 26 - 
**JDK:** OpenJDK 17 or higher - **Node.js:** 
Node.js v18+ with npm (for building 
`editor-core`) - **ABI:** `arm64-v8a`
### Compiling Monaco Editor Core
```bash cd editor-core npm install node 
./node_modules/vite/bin/vite.js build ``` 
*Outputs compiled assets directly into 
`app/src/main/assets/editor/`.*
### Building the Android APK
```bash ./gradlew assembleDebug ``` *Generated 
APK will be located at 
`app/build/outputs/apk/debug/app-debug.apk`.* 
---
## 🗺️ Roadmap
- [x] **Monaco Core Touch Stabilization:** 
Single-touch handles, OS clipboard bridge, IME 
protection. - [x] **VS Code-Style Settings 
Engine:** JSON persistence, reactive 
hot-reloading, AI agent readiness. - [x] 
**Multi-Mode Auto-Save:** Debounced delay, app 
blur save, dirty buffer indicators. - [ ] 
**Quick-Access Symbol Bar:** Extended developer 
keyboard row for brackets, quotes, and 
punctuation. - [ ] **In-App Live Preview:** 
Split-pane HTML/CSS/JS live web server preview 
with mobile inspect element. - [ ] **Git Version 
Control UI:** Stage, commit, push, pull, and 
branch management inside the app. - [ ] **AI 
Assistant / Copilot:** Built-in Gemini API 
integration for automated bug fixing and code 
generation. --- <div align="center"> Crafted 
with ❤️ for mobile developers who build anywhere, 
anytime. </div>
