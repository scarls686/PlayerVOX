"""
tfa_to_playervox.py
将 TFA-VOX 语音包 .lua 文件转换为 PlayerVOX 语音包格式。

用法:
    python tfa_to_playervox.py <lua文件路径>

输出:
    output/
    ├── pack_meta.json
    └── data/<pack_id>/vox/triggers/
        ├── death.json, login.json, respawn.json, hurt.json,
        ├── kill.json, low_health.json, tacz_reload.json,
        ├── tacz_shoot.json, tacz_kill.json, tacz_melee.json
        └── custom_<id>.json  (callouts)
"""

import re
import json
import os
import sys

# ---------------------------------------------------------------------------
# 常量
# ---------------------------------------------------------------------------

# cooldown 默认值（tick）
COOLDOWN = {
    "death":        600,
    "login":        20,
    "respawn":      20,
    "hurt":         60,
    "kill":         200,
    "low_health":   200,
    "tacz_reload":  200,
    "tacz_shoot":   60,
    "tacz_kill":    200,
    "tacz_melee":   200,
    "custom":       200,
}

# low_health 的血量阈值（crithit/crithealth 共用）
LOW_HEALTH_PERCENT = 0.35

# ---------------------------------------------------------------------------
# Lua 解析
# ---------------------------------------------------------------------------

def parse_lua(text):
    """
    从 TFA-VOX lua 文本中提取所有事件和对应的音效文件列表。
    返回结构：
    {
        "main":    { "death": ["path/a.ogg", ...], "spawn": [...], ... },
        "murder":  { "generic": [...], "zombie": [...], ... },
        "damage":  { "HITGROUP_GENERIC": [...], ... },
        "callouts": { "agree": [...], ... },
        ...
    }
    """
    result = {}

    # 去掉 Lua 注释（--[[ ... ]] 和 -- ...）
    text = re.sub(r'--\[\[.*?\]\]', '', text, flags=re.DOTALL)
    text = re.sub(r'--[^\n]*', '', text)

    # 找到语音包表的开头，支持两种写法：
    #   VOXPackTable = {          （模板格式）
    #   TFAVOX_Models[model] = {  （真实包格式）
    pack_match = re.search(r'(?:VOXPackTable|TFAVOX_Models\s*\[\s*\w+\s*\])\s*=\s*\{', text)
    if not pack_match:
        return result

    # 提取所有 TFAVOX_GenerateSound 调用：
    # TFAVOX_GenerateSound( mdlprefix, "event", { "snd1", "snd2", ... } )
    # 我们需要知道这个调用属于哪个子表和事件键
    # 策略：找到每个 ['key'] = { ... ['sound'] = TFAVOX_GenerateSound(...) }
    # 用状态机按括号深度定位

    # 先提取所有 subtable 块
    # 格式：['subtable'] = { ['event'] = { ... } }
    subtable_pattern = re.compile(
        r"\['(\w+)'\]\s*=\s*\{([^{}]*(?:\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}[^{}]*)*)\}",
        re.DOTALL
    )

    # 因为嵌套结构复杂，改用逐字符括号计数来提取顶层子表
    subtables = extract_subtables(text[pack_match.end():])

    for subtable_name, subtable_content in subtables.items():
        result[subtable_name] = {}
        events = extract_subtables(subtable_content)
        for event_name, event_content in events.items():
            sounds = extract_sounds(event_content)
            if sounds:
                result[subtable_name][event_name] = sounds

    return result


def extract_subtables(text):
    """
    从 lua 表文本中提取顶层 key→内容 映射。
    支持 ['key'] 和 [ENUM_KEY] 两种键格式。
    返回 { key_str: content_str }
    """
    result = {}
    i = 0
    length = len(text)

    while i < length:
        # 匹配键：['name'] 或 [ENUM_NAME] 或 ["name"]
        key_match = re.match(r'''\s*\[\s*['"]?(\w+)['"]?\s*\]\s*=\s*\{''', text[i:])
        if not key_match:
            i += 1
            continue

        key = key_match.group(1)
        # 找到开括号位置
        brace_start = i + key_match.end() - 1  # 指向 {
        # 用括号计数找到对应的 }
        depth = 0
        j = brace_start
        while j < length:
            if text[j] == '{':
                depth += 1
            elif text[j] == '}':
                depth -= 1
                if depth == 0:
                    break
            j += 1

        content = text[brace_start + 1:j]
        result[key] = content
        i = j + 1

    return result


def extract_sounds(event_content):
    """
    从事件块文本中提取音效路径列表。
    支持：
      TFAVOX_GenerateSound( mdlprefix, "xxx", { "path1", "path2" } )
      ['sound'] = { "path1", "path2" }
      ['sound'] = "path"
      ['sound'] = nil  → 返回空列表
    """
    # nil 快速判断
    nil_match = re.search(r"\['sound'\]\s*=\s*nil", event_content)
    if nil_match:
        return []

    # 找 TFAVOX_GenerateSound 的第三个参数（音效列表）
    gen_match = re.search(
        r'TFAVOX_GenerateSound\s*\([^,]+,[^,]+,\s*\{([^}]*)\}',
        event_content
    )
    if not gen_match:
        # ['sound'] = { "path1", "path2" } 直接写法
        direct_match = re.search(r"\['sound'\]\s*=\s*\{([^}]*)\}", event_content)
        if not direct_match:
            # 单字符串 ['sound'] = "path"
            str_match = re.search(r"\['sound'\]\s*=\s*['\"]([^'\"]+)['\"]", event_content)
            if str_match:
                return [str_match.group(1).strip()]
            return []
        content = direct_match.group(1)
    else:
        content = gen_match.group(1)

    # 提取所有引号内的字符串，过滤空值
    sounds = re.findall(r'["\']([^"\']+)["\']', content)
    return [s.strip() for s in sounds if s.strip()]


# ---------------------------------------------------------------------------
# 音效路径处理
# ---------------------------------------------------------------------------

def process_sound_path(raw_path, pack_id, warnings):
    """
    将 TFA-VOX 音效路径转换为 PlayerVOX sound 字段格式。
    例：
      "takina/death_1.ogg" → "takina:death_1"  (如果 namespace 与 pack_id 一致)
      "snd1"               → "<pack_id>:snd1"   (无路径分隔符，视为相对路径)
      "other/snd.mp3"      → 警告并返回占位符
    """
    # 取文件名（去掉所有目录前缀和扩展名）
    basename = os.path.basename(raw_path.replace('\\', '/'))
    name, ext = os.path.splitext(basename)
    ext = ext.lower()

    if not ext:
        warnings.append(f"无扩展名，默认视为 WAV（需手动转换为 OGG）: {raw_path}")
    elif ext != '.ogg':
        warnings.append(f"非 OGG 文件（需手动转换为 OGG）: {raw_path}")

    return f"{pack_id}:{name}"


def make_entries(sounds, pack_id, warnings, extra_conditions=None, once=False):
    """
    将音效列表转换为 PlayerVOX entries 数组。
    每个音效一个 entry，weight 均为 1。
    extra_conditions: dict，附加到每个 entry 的 conditions 字段。
    """
    entries = []
    for raw in sounds:
        sound_id = process_sound_path(raw, pack_id, warnings)
        entry = {}
        if extra_conditions:
            entry["conditions"] = extra_conditions
        entry["sound"] = sound_id
        entry["weight"] = 1
        if once:
            entry["once"] = True
        entries.append(entry)
    return entries


# ---------------------------------------------------------------------------
# JSON 生成
# ---------------------------------------------------------------------------

def make_trigger_json(trigger, cooldown, entries):
    return {
        "trigger": trigger,
        "cooldown": cooldown,
        "entries": entries
    }


def build_jsons(parsed, pack_id, warnings):
    """
    根据解析结果构建所有 trigger JSON 对象。
    返回 { filename: json_dict }
    """
    outputs = {}
    main = parsed.get("main", {})
    damage = parsed.get("damage", {})
    murder = parsed.get("murder", {})
    callouts = parsed.get("callouts", {})

    # --- death ---
    if "death" in main:
        entries = make_entries(main["death"], pack_id, warnings)
        outputs["death"] = make_trigger_json("death", COOLDOWN["death"], entries)

    # --- login ---
    if "spawn" in main:
        entries = make_entries(main["spawn"], pack_id, warnings)
        outputs["login"] = make_trigger_json("login", COOLDOWN["login"], entries)

    # --- respawn ---
    if "spawn" in main:
        entries = make_entries(main["spawn"], pack_id, warnings)
        outputs["respawn"] = make_trigger_json("respawn", COOLDOWN["respawn"], entries)

    # --- hurt（所有 hitgroup 合并）---
    all_hurt = []
    for sounds in damage.values():
        all_hurt.extend(sounds)
    # 去重（不同 hitgroup 可能有相同音效）
    seen = set()
    deduped = []
    for s in all_hurt:
        if s not in seen:
            seen.add(s)
            deduped.append(s)
    if deduped:
        entries = make_entries(deduped, pack_id, warnings)
        outputs["hurt"] = make_trigger_json("hurt", COOLDOWN["hurt"], entries)

    # --- kill（murder 所有分类合并）---
    all_kill = []
    for sounds in murder.values():
        all_kill.extend(sounds)
    seen = set()
    deduped = []
    for s in all_kill:
        if s not in seen:
            seen.add(s)
            deduped.append(s)
    if deduped:
        entries = make_entries(deduped, pack_id, warnings)
        outputs["kill"] = make_trigger_json("kill", COOLDOWN["kill"], entries)

    # --- low_health（crithit + crithealth + healmax）---
    low_entries = []
    crit_sounds = []
    for key in ("crithit", "crithealth"):
        if key in main:
            crit_sounds.extend(main[key])
    if crit_sounds:
        low_entries.extend(make_entries(
            crit_sounds, pack_id, warnings,
            extra_conditions={
                "min_health_percent": 0.0,
                "max_health_percent": LOW_HEALTH_PERCENT
            }
        ))
    if "healmax" in main:
        low_entries.extend(make_entries(
            main["healmax"], pack_id, warnings,
            extra_conditions={
                "min_health_percent": 1.0,
                "max_health_percent": 1.0
            },
            once=True
        ))
    if low_entries:
        outputs["low_health"] = make_trigger_json("low_health", COOLDOWN["low_health"], low_entries)

    # --- tacz_reload ---
    if "reload" in main:
        entries = make_entries(main["reload"], pack_id, warnings)
        outputs["tacz_reload"] = make_trigger_json("tacz_reload", COOLDOWN["tacz_reload"], entries)
    else:
        outputs["tacz_reload"] = make_trigger_json("tacz_reload", COOLDOWN["tacz_reload"], [])

    # --- tacz 空骨架 ---
    for trig in ("tacz_shoot", "tacz_kill", "tacz_melee"):
        outputs[trig] = make_trigger_json(trig, COOLDOWN[trig], [])

    # --- callouts → custom_<id> ---
    for callout_id, sounds in callouts.items():
        if not sounds:
            continue
        entries = make_entries(sounds, pack_id, warnings)
        filename = f"custom_{callout_id}"
        outputs[filename] = make_trigger_json(callout_id, COOLDOWN["custom"], entries)

    return outputs


# ---------------------------------------------------------------------------
# 文件写出
# ---------------------------------------------------------------------------

def write_outputs(jsons, pack_id, pack_name, out_dir):
    # pack_meta.json
    meta = {
        "id": pack_id,
        "name": pack_name,
        "description": "",
        "icon": "icon.png"
    }
    meta_path = os.path.join(out_dir, "pack_meta.json")
    os.makedirs(out_dir, exist_ok=True)
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    print(f"[生成] pack_meta.json")

    # trigger JSONs
    trigger_dir = os.path.join(out_dir, "data", pack_id, "vox", "triggers")
    os.makedirs(trigger_dir, exist_ok=True)
    for filename, data in jsons.items():
        path = os.path.join(trigger_dir, f"{filename}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        entry_count = len(data.get("entries", []))
        tag = "空骨架" if entry_count == 0 else f"{entry_count} 条 entry"
        print(f"[生成] data/{pack_id}/vox/triggers/{filename}.json  ({tag})")


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 2:
        print("用法: python tfa_to_playervox.py <lua文件路径>")
        sys.exit(1)

    lua_path = sys.argv[1]
    if not os.path.isfile(lua_path):
        print(f"[错误] 找不到文件: {lua_path}")
        sys.exit(1)

    with open(lua_path, "r", encoding="utf-8", errors="replace") as f:
        lua_text = f.read()

    print(f"\n读取文件: {lua_path}\n")

    pack_id = input("Pack ID（如 takina，用于 namespace 和目录名）: ").strip()
    if not pack_id:
        print("[错误] Pack ID 不能为空")
        sys.exit(1)

    pack_name = input("Pack 显示名（如 井之上泷奈）: ").strip()
    if not pack_name:
        pack_name = pack_id

    out_dir = input(f"输出目录 [默认 ./output]: ").strip()
    if not out_dir:
        out_dir = "./output"

    print()

    # 解析
    parsed = parse_lua(lua_text)

    if not parsed:
        print("[警告] 未能从 lua 文件中解析到任何事件，请确认文件格式是否为 TFA-VOX 语音包。")
        sys.exit(1)

    # 统计解析到的事件
    total = sum(len(v) for v in parsed.values())
    print(f"[解析] 共找到 {total} 个事件:")
    for subtable, events in parsed.items():
        for event in events:
            count = len(events[event])
            print(f"       {subtable}.{event}  ({count} 个音效)")

    # 丢弃的事件
    skipped = []
    main_supported = {"death", "spawn", "crithit", "crithealth", "healmax", "reload"}
    for key in parsed.get("main", {}):
        if key not in main_supported:
            skipped.append(f"main.{key}")
    for subtable in ("spot", "taunt", "external"):
        if subtable in parsed:
            for key in parsed[subtable]:
                skipped.append(f"{subtable}.{key}")
    if skipped:
        print(f"\n[跳过] 以下事件无对应 PlayerVOX trigger，已丢弃:")
        for s in skipped:
            print(f"       {s}")

    # 构建 JSON
    warnings = []
    jsons = build_jsons(parsed, pack_id, warnings)

    # 输出警告
    if warnings:
        print(f"\n[警告] 以下音效需要手动转换为 OGG 格式后修改对应 JSON:")
        for w in warnings:
            print(f"       {w}")

    # 写出文件
    print(f"\n[输出] 目录: {out_dir}")
    write_outputs(jsons, pack_id, pack_name, out_dir)

    print(f"\n完成！共生成 {len(jsons) + 1} 个文件。")
    print("提示: tacz_shoot / tacz_kill / tacz_melee 为空骨架，请根据需要手动填写音效。")
    if "icon.png" in open(os.path.join(out_dir, "pack_meta.json")).read():
        print("提示: 请将图标文件放置到 assets/<pack_id>/textures/icon.png。")


if __name__ == "__main__":
    main()
