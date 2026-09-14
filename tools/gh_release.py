#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
发布 GitHub Release：读取 build.gradle 的版本号 -> 找到对应 APK ->
对比上一个 tag 生成简洁更新说明 -> 创建 Release 并上传 APK。

用法（在项目根目录执行）：
    python tools/gh_release.py

Token 获取优先级：
    1. 环境变量 GH_TOKEN
    2. git credential 中 github.com 的 password
"""
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "baoru0908/timestamp-recorder"  # 账号已由 BaoruLee 改名，须用新名字，旧名仅靠重定向
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Release red line: the APK attached to a GitHub Release MUST be signed with
# release.keystore (CN=TimestampRecorder). Users upgrade by overwriting the
# installed app, so a debug or otherwise-differently-signed build would be
# rejected by Android with a signature conflict. This fingerprint is the
# release keystore's certificate SHA-256 digest -- do not change it by hand.
RELEASE_CERT_SHA256 = "cca83079a87053a579262dfd8db5191af36349aacd2db26b5c5c67daf8f976ce"


def run(cmd, capture=True):
    return subprocess.run(cmd, capture_output=capture, text=True)


def find_apksigner():
    """Locate apksigner from the Android SDK (build-tools, newest first)."""
    bases = []
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        v = os.environ.get(var)
        if v:
            bases.append(os.path.join(v, "build-tools"))
    lp = os.path.join(ROOT, "local.properties")
    if os.path.exists(lp):
        with open(lp, encoding="utf-8", errors="ignore") as f:
            for line in f:
                if line.strip().startswith("sdk.dir="):
                    sdk = line.split("=", 1)[1].strip().replace("\\\\", "\\")
                    bases.append(os.path.join(sdk, "build-tools"))
    la = os.environ.get("LOCALAPPDATA")
    if la:
        bases.append(os.path.join(la, "Android", "Sdk", "build-tools"))
    for base in bases:
        if not os.path.isdir(base):
            continue
        for ver in sorted(os.listdir(base), reverse=True):
            for name in ("apksigner.bat", "apksigner"):
                p = os.path.join(base, ver, name)
                if os.path.exists(p):
                    return p
    return None


def run_tool(cmd):
    """Run an external tool; .bat wrappers on Windows may need cmd.exe."""
    try:
        return subprocess.run(cmd, capture_output=True, text=True)
    except OSError:
        comspec = os.environ.get("COMSPEC", "cmd.exe")
        return subprocess.run([comspec, "/c"] + cmd, capture_output=True, text=True)


def verify_release_signature(apk):
    """Refuse to publish anything that is not the release-signed APK."""
    signer = find_apksigner()
    if not signer:
        sys.exit(
            "找不到 apksigner，无法校验 APK 签名。\n"
            "请确认 Android SDK 存在（ANDROID_HOME / local.properties），\n"
            "并统一用 .\\tools\\release.ps1 构建签名包。"
        )
    res = run_tool([signer, "verify", "--print-certs", apk])
    out = (res.stdout or "") + (res.stderr or "")
    if res.returncode != 0:
        sys.exit("APK 未签名或已损坏，拒绝发布：\n" + out)
    digest = ""
    for line in out.splitlines():
        if "Signer #1 certificate SHA-256 digest" in line:
            digest = line.split(":")[-1].strip().lower().replace(":", "")
            break
    if not digest:
        sys.exit("无法读取 APK 签名指纹，拒绝发布。")
    if digest != RELEASE_CERT_SHA256:
        if "Android Debug" in out:
            sys.exit(
                "拒绝发布：该 APK 是 debug 签名，用户无法覆盖安装。\n"
                "请执行 .\\tools\\release.ps1 重新构建 release 签名包。"
            )
        sys.exit(
            "拒绝发布：APK 签名与 release.keystore 不一致，\n"
            "用户安装时会因签名冲突失败（无法覆盖升级）。\n"
            "  实际: %s\n  期望: %s\n"
            "请执行 .\\tools\\release.ps1 重新构建签名包。" % (digest, RELEASE_CERT_SHA256)
        )
    print("签名校验通过：CN=TimestampRecorder (%s...)" % digest[:16])


def read_version():
    with open(os.path.join(ROOT, "app", "build.gradle"), encoding="utf-8") as f:
        txt = f.read()
    m = re.search(r'versionName\s+"([^"]+)"', txt)
    if not m:
        sys.exit("无法从 app/build.gradle 解析 versionName")
    return m.group(1)


def get_token():
    token = os.environ.get("GH_TOKEN")
    if token:
        return token.strip()
    res = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n",
        capture_output=True, text=True,
    )
    for line in res.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    sys.exit("未找到 GitHub Token：请设置环境变量 GH_TOKEN 或用 git credential 登录 github.com")


def prev_tag(version):
    tags = run(["git", "tag", "--list"], capture=True).stdout.split()
    prev = None
    for t in sorted(tags):
        if t != "v" + version:
            prev = t
    return prev


def parse_version(v):
    parts = v.split(".")
    while len(parts) < 3:
        parts.append("0")
    return int(parts[0]), int(parts[1]), int(parts[2])


def read_readme_intro():
    """大版本用：从 README.md 提取主体作为完整介绍（跳过开头 badge / div）。"""
    p = os.path.join(ROOT, "README.md")
    if not os.path.exists(p):
        return ""
    txt = open(p, encoding="utf-8").read()
    idx = txt.find("\n# ")
    if idx == -1:
        idx = txt.find("# ")
    if idx != -1:
        txt = txt[idx + 1:]
    return txt.strip()


def split_commits(commits):
    """中版本用：按前缀把提交分成『新功能』与『其他变更』。"""
    feats, others = [], []
    for c in commits:
        if re.match(r"^(feat|add|新增|加入|feature)\b", c, re.I):
            feats.append("- " + c)
        else:
            others.append("- " + c)
    return "\n".join(feats), "\n".join(others)


def api(path, data=None, method="GET"):
    url = "https://api.github.com" + path
    req = urllib.request.Request(
        url, data=json.dumps(data).encode() if data else None, method=method
    )
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode()
            if not raw.strip():
                return None
            return json.loads(raw)
    except urllib.error.HTTPError as e:
        sys.exit("API ERROR %s: %s" % (e.code, e.read().decode()))


def delete_existing(tag):
    """若已存在同名 tag 的 Release，先删除（保证幂等、避免重复创建）。"""
    rels = api("/repos/%s/releases" % REPO, method="GET")
    if not isinstance(rels, list):
        return
    for r in rels:
        if r.get("tag_name") == tag:
            api("/repos/%s/releases/%s" % (REPO, r["id"]), method="DELETE")
            print("已删除旧 Release: %s" % tag)
            return


def main():
    global TOKEN
    TOKEN = get_token()
    version = read_version()
    tag = "v" + version
    apk = os.path.join(ROOT, "TimestampRecorder_v%s.apk" % version)
    if not os.path.exists(apk):
        sys.exit("找不到 APK：%s\n请先执行 .\\tools\\release.ps1 构建签名。" % apk)

    # 发布红线：只允许上传 release 签名的包（用户靠覆盖安装升级）。
    verify_release_signature(apk)

    major, minor, patch = parse_version(version)
    prev = prev_tag(version)
    since = prev if prev else ""
    rng = (since + "..HEAD") if since else "HEAD"
    log_raw = run(["git", "log", rng, "--pretty=format:%s"], capture=True).stdout.strip()
    commits = [c for c in log_raw.splitlines() if c.strip()]

    # 版本类型决定更新说明详略：
    #   大版本（x.0.0）= 完整重新介绍一遍（含 README 主体）
    #   中版本（x.y.0）= 介绍新功能（新功能详细，其余简洁）
    #   小版本（x.y.z, z>0）= 极简变更列表
    if minor == 0 and patch == 0:
        intro = read_readme_intro()
        body = ("# 时间戳记录 v%s 正式发布\n\n" % version) + intro + \
               "\n\n> 覆盖安装不会丢失已有事件与记录数据。完整更新历史见各版本 Releases。"
    elif minor > 0 and patch == 0:
        feats, others = split_commits(commits)
        body = "## 新功能\n" + (feats or "- （见下方变更）") + \
               "\n\n## 其他变更\n" + (others or "- 无") + \
               "\n\n> 覆盖安装不会丢失已有事件与记录数据。"
    else:
        log = "\n".join("- " + c for c in commits) or ("- 发布 v%s" % version)
        body = ("## 相比 %s 的变更\n\n%s\n\n" % (prev or "上一版", log)) + \
               "> 覆盖安装不会丢失已有事件与记录数据。"

    print("版本: %s  类型: %s  对比基准: %s" % (
        tag,
        "大版本" if (minor == 0 and patch == 0) else ("中版本" if minor > 0 and patch == 0 else "小版本"),
        prev or "(首个版本)"))
    delete_existing(tag)

    rel = api("/repos/%s/releases" % REPO, {
        "tag_name": tag,
        "name": "v" + version,
        "body": body,
        "generate_release_notes": False,
    }, method="POST")
    if not isinstance(rel, dict):
        sys.exit("创建 Release 返回异常响应: %r" % (rel,))
    rel_id = rel["id"]
    upload_url = rel["upload_url"].split("{")[0]

    with open(apk, "rb") as f:
        data = f.read()
    req = urllib.request.Request(
        upload_url + "?name=" + os.path.basename(apk), data=data, method="POST"
    )
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Content-Type", "application/vnd.android.package-archive")
    with urllib.request.urlopen(req) as r:
        print("APK 上传完成: HTTP %s" % r.status)

    print("RELEASE_URL=" + rel["html_url"])


if __name__ == "__main__":
    main()
