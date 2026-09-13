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

REPO = "BaoruLee/timestamp-recorder"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def run(cmd, capture=True):
    return subprocess.run(cmd, capture_output=capture, text=True)


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
        return token
    out = run(["git", "credential", "fill"], capture=True)
    if out.returncode == 0:
        inp = "protocol=https\nhost=github.com\n"
        res = subprocess.run(
            ["git", "credential", "fill"],
            input=inp, capture_output=True, text=True,
        )
        for line in res.stdout.splitlines():
            if line.startswith("password="):
                return line.split("=", 1)[1]
    sys.exit("未找到 GitHub Token：请设置环境变量 GH_TOKEN 或用 git credential 登录 github.com")


def prev_tag(version):
    tags = run(["git", "tag", "--list"], capture=True).stdout.split()
    prev = None
    for t in sorted(tags):
        if t != "v" + version:
            prev = t
    return prev


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
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        sys.exit("API ERROR %s: %s" % (e.code, e.read().decode()))


def main():
    global TOKEN
    TOKEN = get_token()
    version = read_version()
    tag = "v" + version
    apk = os.path.join(ROOT, "TimestampRecorder_v%s.apk" % version)
    if not os.path.exists(apk):
        sys.exit("找不到 APK：%s\n请先执行 .\\tools\\release.ps1 构建签名。" % apk)

    prev = prev_tag(version)
    since = prev if prev else ""
    rng = (since + "..HEAD") if since else "HEAD"
    log = run(["git", "log", rng, "--pretty=format:- %s"], capture=True).stdout.strip()
    if not log:
        log = "- 发布 v%s" % version

    body = (
        "## 相比 %s 的变更\n\n%s\n\n"
        "> 覆盖安装不会丢失已有事件与记录数据。"
        % (prev or "上一版", log)
    )

    print("版本: %s  对比基准: %s" % (tag, prev or "(首个版本)"))
    rel = api("/repos/%s/releases" % REPO, {
        "tag_name": tag,
        "name": "v" + version,
        "body": body,
        "generate_release_notes": False,
    })
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
