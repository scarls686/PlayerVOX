"""
audio_convert.py
批量将音频文件转换为单声道 OGG Vorbis 格式（PlayerVOX 所需格式）。
依赖：ffmpeg（需在 PATH 中，或与脚本同目录）

用法:
    python audio_convert.py <输入目录> [输出目录]

    输入目录：包含 WAV/MP3/OGG 文件的目录（会递归扫描子目录）
    输出目录：可选，默认为 <输入目录>_converted

支持格式：WAV, MP3, OGG, FLAC, M4A, AAC
"""

import os
import sys
import shutil
import subprocess

SUPPORTED_EXTS = {".wav", ".mp3", ".ogg", ".flac", ".m4a", ".aac"}


def find_ffmpeg():
    """查找 ffmpeg，优先同目录，其次 PATH。"""
    # 同目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    for name in ("ffmpeg.exe", "ffmpeg"):
        candidate = os.path.join(script_dir, name)
        if os.path.isfile(candidate):
            return candidate

    # PATH
    found = shutil.which("ffmpeg")
    if found:
        return found

    return None


def collect_files(input_dir):
    """递归收集所有支持格式的音频文件。"""
    result = []
    for root, _, files in os.walk(input_dir):
        for fname in files:
            ext = os.path.splitext(fname)[1].lower()
            if ext in SUPPORTED_EXTS:
                result.append(os.path.join(root, fname))
    return sorted(result)


def convert_file(ffmpeg, src_path, dst_path):
    """
    调用 ffmpeg 将 src 转换为单声道 OGG Vorbis，输出到 dst。
    返回 (success: bool, message: str)
    """
    os.makedirs(os.path.dirname(dst_path), exist_ok=True)

    cmd = [
        ffmpeg,
        "-y",           # 覆盖已有文件
        "-i", src_path,
        "-ac", "1",     # 单声道
        "-c:a", "libvorbis",
        "-q:a", "4",    # 质量等级 4（约 128kbps，平衡质量与体积）
        dst_path
    ]

    try:
        result = subprocess.run(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            timeout=60
        )
        if result.returncode != 0:
            err = result.stderr.decode("utf-8", errors="replace").strip()
            # 只取最后一行错误信息
            last_line = [l for l in err.splitlines() if l.strip()]
            msg = last_line[-1] if last_line else "未知错误"
            return False, msg
        return True, ""
    except subprocess.TimeoutExpired:
        return False, "转换超时（超过 60 秒）"
    except FileNotFoundError:
        return False, "ffmpeg 不可用"


def main():
    if len(sys.argv) < 2:
        print("用法: python audio_convert.py <输入目录> [输出目录]")
        sys.exit(1)

    input_dir = sys.argv[1]
    if not os.path.isdir(input_dir):
        print(f"[错误] 找不到目录: {input_dir}")
        sys.exit(1)

    output_dir = sys.argv[2] if len(sys.argv) >= 3 else input_dir.rstrip("/\\") + "_converted"

    # 检测 ffmpeg
    ffmpeg = find_ffmpeg()
    if not ffmpeg:
        print("[错误] 找不到 ffmpeg。")
        print("  请安装 ffmpeg 并确保其在 PATH 中，或将 ffmpeg.exe 放到脚本同目录。")
        print("  下载地址: https://ffmpeg.org/download.html")
        sys.exit(1)

    print(f"ffmpeg: {ffmpeg}")
    print(f"输入:   {input_dir}")
    print(f"输出:   {output_dir}\n")

    # 收集文件
    files = collect_files(input_dir)
    if not files:
        print("[警告] 未找到任何支持格式的音频文件。")
        sys.exit(0)

    print(f"共找到 {len(files)} 个文件，开始转换...\n")

    ok_count = 0
    skip_count = 0
    fail_count = 0
    failed_files = []

    for i, src in enumerate(files, 1):
        # 计算输出路径：保留相对目录结构，扩展名改为 .ogg
        rel = os.path.relpath(src, input_dir)
        rel_ogg = os.path.splitext(rel)[0] + ".ogg"
        dst = os.path.join(output_dir, rel_ogg)

        src_ext = os.path.splitext(src)[1].lower()
        label = f"[{i}/{len(files)}]"

        # 已经是 OGG 但仍需转换（可能是立体声）
        # 统一走转换流程，ffmpeg 会处理单声道
        success, msg = convert_file(ffmpeg, src, dst)

        if success:
            print(f"{label} OK  {rel}")
            ok_count += 1
        else:
            print(f"{label} 失败  {rel}")
            print(f"       原因: {msg}")
            fail_count += 1
            failed_files.append(rel)

    # 汇总
    print(f"\n完成。成功 {ok_count}，失败 {fail_count}。")
    if failed_files:
        print("\n失败文件列表:")
        for f in failed_files:
            print(f"  {f}")


if __name__ == "__main__":
    main()
