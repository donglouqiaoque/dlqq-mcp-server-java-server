# -*- coding: utf-8 -*-
r"""把完整 CPython 安装裁剪成本项目用的 BLE 运行时 .runtime\python
================================================================
只保留解释器、标准库、tkinter/Tcl-Tk、ensurepip 和 BLE 依赖
（bleak + ttkbootstrap）；源安装里的 site-packages 一个都不复制，
torch / numpy / bpy 这类包不会混进来。

用法（由同目录的 make_python_env.bat 调用）：
    python .runtime\make_python_env.py --src <Python安装目录> --out .runtime\python
    python .runtime\make_python_env.py --src <Python安装目录> --dry-run
    python .runtime\make_python_env.py --src <Python安装目录> --wheels <wheel目录>

说明：
    · 源必须是完整 CPython 安装（带 tkinter 与 Tcl/Tk）；
    · 目标已存在且非空时要 --force，或在终端里回答 y；
    · 只依赖标准库，任何 3.10+ 的 python 都能运行它。
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time

for _s in ("stdout", "stderr"):
    try:
        getattr(sys, _s).reconfigure(encoding="utf-8", errors="replace",
                                     line_buffering=True)
    except (AttributeError, ValueError):
        pass

# 工程根 = .runtime\ 的上一级；相对 --out 按它解析，不受当前工作目录影响
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 顶层只保留解释器本体和运行库；include / Doc / Scripts 等安装残留不复制
ROOT_FILES = {"python.exe", "pythonw.exe", "python3.dll",
              "vcruntime140.dll", "vcruntime140_1.dll"}
RE_ROOT_DLL = re.compile(r"^python3\d+\.dll$", re.I)
RE_ROOT_ZIP = re.compile(r"^python3\d+\.zip$", re.I)

# Lib 下整目录排除（tkinter / ensurepip 必须保留）
DIR_SKIP = {
    "Lib/site-packages", "Lib/test", "Lib/idlelib", "Lib/turtledemo",
}


def skip_file(low):
    """调试版、自由线程版、pdb 一律不要。"""
    if low.endswith(".pdb"):
        return True
    if low.endswith("_d.pyd") or low.endswith("_d.dll") or low.endswith("_d.exe"):
        return True
    if low in ("python_d.exe", "pythonw_d.exe"):
        return True
    if re.search(r"\.cp\d+t-", low) or re.search(r"\.cp\d+t\.", low):
        return True
    return False


def keep_files(src):
    """产出要保留的文件：顶层白名单 + DLLs/ + Lib/ + tcl/。"""
    for dirpath, dirnames, filenames in os.walk(src):
        rel = os.path.relpath(dirpath, src)
        rel = "" if rel == "." else rel.replace(os.sep, "/")
        if rel == "":
            dirnames[:] = [d for d in dirnames if d in ("DLLs", "Lib", "tcl")]
            for fn in sorted(filenames):
                low = fn.lower()
                if low in ROOT_FILES or RE_ROOT_DLL.match(low) or RE_ROOT_ZIP.match(low):
                    yield os.path.join(dirpath, fn)
            continue
        dirnames[:] = sorted(
            d for d in dirnames
            if d != "__pycache__"
            and (f"{rel}/{d}" if rel else d) not in DIR_SKIP)
        for fn in sorted(filenames):
            if skip_file(fn.lower()):
                continue
            yield os.path.join(dirpath, fn)


def run(exe, args, timeout=600):
    return subprocess.run([exe] + args, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=timeout)


def main():
    ap = argparse.ArgumentParser(description="裁剪一份 BLE 用的 Python 运行时")
    ap.add_argument("--src",
                    help="完整 CPython 安装目录（也可以直接给 python.exe）；不传则提示键盘输入")
    ap.add_argument("--out", default=".runtime/python",
                    help="输出目录，默认 .runtime/python")
    ap.add_argument("--force", action="store_true", help="目标已存在时直接覆盖")
    ap.add_argument("--no-pip", action="store_true", help="不安装 pip / bleak / ttkbootstrap")
    ap.add_argument("--wheels", default="", help="离线 wheel 目录（给出时使用 --no-index）")
    ap.add_argument("--dry-run", action="store_true", help="只统计，不写文件")
    a = ap.parse_args()

    src_arg = (a.src or "").strip()
    if not src_arg:
        try:
            src_arg = input("Python 安装目录（含 python.exe）: ").strip()
        except EOFError:
            src_arg = ""
        src_arg = src_arg.strip().strip('"').strip("'")
    if not src_arg:
        print("[错误] 没有提供 Python 安装目录")
        return 1

    src = os.path.abspath(src_arg)
    if os.path.isfile(src) and src.lower().endswith(".exe"):
        src = os.path.dirname(src)
    src_py = os.path.join(src, "python.exe")
    out = a.out if os.path.isabs(a.out) else os.path.join(ROOT, a.out)

    print("=" * 64)
    print("  dlqq-mcp-server BLE 运行时（tkinter + bleak + ttkbootstrap）")
    print("=" * 64)
    print(f"[源] {src}")
    print(f"[输出] {out}")

    if not os.path.isfile(src_py):
        print(f"[错误] 这里没有 python.exe：{src}")
        return 1

    r = run(src_py, ["-c", "import sys;print('%d.%d' % sys.version_info[:2]);print(sys.base_prefix)"])
    if r.returncode != 0:
        print(f"[错误] 跑不了这个 python：{src_py}")
        print((r.stderr or "").strip()[-800:])
        return 1
    lines = (r.stdout or "").strip().splitlines()
    ver = lines[0] if lines else "?"
    base = lines[1] if len(lines) > 1 else src
    if tuple(int(x) for x in ver.split(".")) < (3, 10):
        print(f"[错误] 需要 Python 3.10+，这个是 {ver}")
        return 1
    if base and os.path.isdir(base) and os.path.abspath(base) != src:
        print(f"[提示] 这是虚拟环境，改用它的 base：{base}")
        src = os.path.abspath(base)
        src_py = os.path.join(src, "python.exe")

    if not os.path.isdir(os.path.join(src, "Lib", "tkinter")):
        print("[错误] 源安装里没有 Lib\\tkinter，BLE 界面需要 tkinter")
        return 1

    files = list(keep_files(src))
    total = sum(os.path.getsize(p) for p in files)
    print(f"[计划] {len(files)} 个文件 / {total / 1048576:.1f} MB"
          "（已排除 site-packages、测试、idlelib；保留 tkinter/Tcl-Tk）")

    if a.dry_run:
        print("[dry-run] 没有写任何文件")
        return 0

    if os.path.isdir(out) and os.listdir(out):
        if not a.force:
            try:
                ans = input(f"[确认] {out} 已存在，覆盖？[y/N] ").strip().lower()
            except EOFError:
                ans = ""
            if ans not in ("y", "yes"):
                print("[取消] 什么都没改")
                return 1

    # 先构建到同级临时目录，成功后再替换；中途失败不会破坏现有运行时
    build = out + ".build"
    if os.path.isdir(build):
        shutil.rmtree(build)
    os.makedirs(build, exist_ok=True)

    done = 0
    for fp in files:
        rel = os.path.relpath(fp, src)
        dst = os.path.join(build, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(fp, dst)
        done += 1
        if done % 500 == 0:
            print(f"  复制中 {done}/{len(files)} ...")
    print(f"[复制] 完成 {done} 个文件")

    build_py = os.path.join(build, "python.exe")
    if not os.path.isfile(build_py):
        print("[错误] 复制完没有 python.exe，构建失败")
        return 1

    if not a.no_pip:
        print("[pip] ensurepip ...")
        r = run(build_py, ["-s", "-m", "ensurepip", "--upgrade"])
        if r.returncode != 0:
            print("[错误] ensurepip 失败：")
            print((r.stderr or r.stdout or "").strip()[-1200:])
            return 1

        print("[pip] 安装 bleak + ttkbootstrap + pillow ...")
        pip_args = ["-s", "-m", "pip", "install", "--no-warn-script-location",
                    "--disable-pip-version-check", "bleak", "ttkbootstrap", "pillow"]
        if a.wheels:
            wheels = a.wheels if os.path.isabs(a.wheels) else os.path.join(ROOT, a.wheels)
            if not os.path.isdir(wheels):
                print(f"[错误] wheel 目录不存在：{wheels}")
                return 1
            pip_args = ["-s", "-m", "pip", "install", "--no-warn-script-location",
                        "--disable-pip-version-check", "--no-index",
                        "--find-links", wheels, "bleak", "ttkbootstrap", "pillow"]
        r = run(build_py, pip_args, timeout=1800)
        if r.returncode != 0:
            print("[错误] pip 安装失败：")
            print((r.stderr or r.stdout or "").strip()[-1500:])
            return 1

    check = "import tkinter, ssl, sqlite3, asyncio, json, socket, threading"
    if not a.no_pip:
        check += ", PIL, bleak, ttkbootstrap"
    print("[自检] 导入标准库与依赖 ...")
    r = run(build_py, ["-s", "-P", "-c", check + ";print('ok')"])
    if r.returncode != 0:
        print("[错误] 新环境自检失败：")
        print((r.stderr or r.stdout or "").strip()[-1200:])
        return 1

    r = run(build_py, ["-s", "-P", "-c", "import sys;print(sys.version.split()[0])"])
    out_ver = (r.stdout or "").strip() or ver

    stamp = time.strftime("%Y-%m-%d %H:%M:%S")
    with open(os.path.join(build, "VERSION.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(f"Python {out_ver}\n")
        f.write(f"built {stamp}\n")
        f.write(f"files {done}, size {total / 1048576:.1f} MB\n")
        f.write(f"source {src}\n")
        f.write("packages bleak, ttkbootstrap, pillow\n" if not a.no_pip else "packages none\n")

    # 许可文件（随包分发需要）：CPython LICENSE + Tcl/Tk license.terms
    lic_dir = os.path.join(build, "licenses")
    os.makedirs(lic_dir, exist_ok=True)
    src_license = os.path.join(src, "LICENSE.txt")
    if os.path.isfile(src_license):
        shutil.copy2(src_license, os.path.join(build, "LICENSE.txt"))
        shutil.copy2(src_license, os.path.join(lic_dir, "PYTHON-LICENSE.txt"))
    tcl_licenses = []
    tcl_root = os.path.join(src, "tcl")
    if os.path.isdir(tcl_root):
        for root, _, names in os.walk(tcl_root):
            for n in names:
                if n.lower() in ("license.terms", "license.txt"):
                    tcl_licenses.append(os.path.join(root, n))
    for i, lp in enumerate(tcl_licenses, 1):
        shutil.copy2(lp, os.path.join(lic_dir, f"TCL-TK-license-{i}.terms"))
    print(f"[许可] CPython LICENSE={'yes' if os.path.isfile(src_license) else 'no'}, "
          f"Tcl/Tk license.terms={len(tcl_licenses)}")

    # 原子替换：先把旧目录改名，再把新目录换上去；失败就恢复旧目录
    old = out + ".old"
    if os.path.isdir(old):
        shutil.rmtree(old)
    if os.path.isdir(out):
        os.replace(out, old)
    try:
        os.replace(build, out)
    except Exception:
        if os.path.isdir(old):
            os.replace(old, out)
        raise
    if os.path.isdir(old):
        shutil.rmtree(old)

    print()
    print("=" * 64)
    print(f"[完成] .runtime\\python 已就绪：Python {out_ver} / {done} 个文件 / "
          f"{total / 1048576:.1f} MB")
    print("       试跑：.runtime\\python\\python.exe -c \"import bleak, ttkbootstrap\"")
    print("=" * 64)
    return 0


if __name__ == "__main__":
    sys.exit(main())
