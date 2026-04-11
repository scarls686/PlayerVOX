# PlayerVOX

**PlayerVOX** is a Minecraft Forge mod that gives players a voice. Using data-pack-style JSON files and OGG audio, voice pack authors can define exactly when a character speaks — on taking damage, scoring a kill, dying, gaining an advancement, and more.

Each player independently selects their own voice pack in-game. Voices play spatially so nearby players can hear them.

---

## Requirements

- Minecraft **1.20.1**
- Forge **47.2.0** or later
- **TACZ** (optional) — enables gun-related voice triggers

---

## Installation

1. Download the latest `PlayerVOX-*.jar` from [Releases](../../releases).
2. Place it in your `mods/` folder.
3. *(Optional)* Place TACZ in `mods/` to enable gun triggers.
4. Launch the game.

---

## Installing Voice Packs

1. Create a `vox_packs/` folder in your Minecraft instance root (same level as `mods/`).
2. Place voice pack folders or `.zip` files inside it.
3. Reload or restart the game.

Voice packs are loaded automatically on server startup and when data packs reload (`/reload`).

---

## Selecting a Voice Pack

Press **V** (default keybind, rebindable) to open the voice pack selection screen.  
Click a pack to select it. Your selection is saved server-side and persists across sessions.

Choose **"无语音包" / "No voice pack"** to disable voices for yourself.

---

## For Voice Pack Authors

See the **[Wiki](../../wiki)** for the full authoring guide, including:

- [Voice Pack Structure](../../wiki/Voice-Pack-Structure)
- [Trigger Reference](../../wiki/Trigger-Reference)
- [Weight & Cooldown](../../wiki/Weight-and-Cooldown)
- [Once Mechanism](../../wiki/Once-Mechanism)
- [TACZ Compatibility](../../wiki/TACZ-Compatibility)
- [Command Reference](../../wiki/Command-Reference)

---

## License

MIT

---

---

# PlayerVOX

**PlayerVOX** 是一个 Minecraft Forge 模组，为玩家角色添加语音。语音包创作者通过 JSON 数据文件和 OGG 音频，精确定义角色在何时发声——受伤、击杀、死亡、获得进度等。

每位玩家在游戏内独立选择自己的语音包，语音以空间音效播放，附近玩家均可听到。

---

## 需求

- Minecraft **1.20.1**
- Forge **47.2.0** 或更高
- **TACZ**（可选）— 启用枪械相关语音触发器

---

## 安装

1. 从 [Releases](../../releases) 下载最新的 `PlayerVOX-*.jar`。
2. 放入 `mods/` 文件夹。
3. *（可选）* 将 TACZ 放入 `mods/` 以启用枪械触发器。
4. 启动游戏。

---

## 安装语音包

1. 在 Minecraft 实例根目录（与 `mods/` 同级）创建 `vox_packs/` 文件夹。
2. 将语音包文件夹或 `.zip` 放入其中。
3. 重载或重启游戏。

语音包在服务器启动时以及执行 `/reload` 时自动加载。

---

## 选择语音包

按 **V**（默认快捷键，可在设置中修改）打开语音包选择界面。  
点击语音包即可选择，选择结果保存在服务端，重新登录后保留。

选择**"无语音包"**可为自己禁用语音。

---

## 语音包创作者

请查阅 **[Wiki](../../wiki)** 获取完整制作指南，包括：

- [语音包结构](../../wiki/Voice-Pack-Structure)
- [触发器参考](../../wiki/Trigger-Reference)
- [权重与冷却](../../wiki/Weight-and-Cooldown)
- [once 机制](../../wiki/Once-Mechanism)
- [TACZ 兼容](../../wiki/TACZ-Compatibility)
- [命令参考](../../wiki/Command-Reference)

---

## 许可证

MIT
