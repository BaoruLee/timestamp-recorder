#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""就地修补已发布的 Release：只改说明正文 + 覆盖同名 APK 附件。

与 gh_release.py 的区别：
    gh_release.py        删掉同名 Release 重建（适合发新版本）
    gh_update_release.py 原地更新（适合「发完了才发现说明写错」这种救火场景）

不删除 Release、不动 tag 指向，因此不会有「tag 指向被重写的旧提交」那类副作用。

用法（在项目根目录执行，走本机代理）：
    python tools/gh_update_release.py            # tag 取当前 versionName
    python tools/gh_update_release.py v3.0.0     # 指定 tag
    python tools/gh_update_release.py --no-apk   # 只改说明，不动附件
"""
import importlib.util
import json
import os
import socket
import sys
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

# 复用 gh_release.py 里的 REPO / token / 版本号 / 签名校验（import 不会执行它的 main）
_spec = importlib.util.spec_from_file_location(
    "gh_release", os.path.join(HERE, "gh_release.py")
)
gr = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gr)

TOKEN = ""


def api(path, data=None, method="GET", base="https://api.github.com"):
    req = urllib.request.Request(
        base + path, data=json.dumps(data).encode() if data else None, method=method
    )
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode()
            return json.loads(raw) if raw.strip() else None
    except urllib.error.HTTPError as e:
        sys.exit("API ERROR %s %s: %s" % (e.code, path, e.read().decode()))


def find_release(tag):
    rels = api("/repos/%s/releases" % gr.REPO)
    for r in rels or []:
        if r.get("tag_name") == tag:
            return r
    sys.exit("找不到 tag 为 %s 的 Release（先确认已发布，或改用 gh_release.py 新建）" % tag)


def upload_asset(release, apk):
    """同名附件先删再传，避免留下两个同名文件。"""
    name = os.path.basename(apk)
    for a in release.get("assets", []):
        if a.get("name") == name:
            api("/repos/%s/releases/assets/%s" % (gr.REPO, a["id"]), method="DELETE")
            print("已删除旧附件: %s" % name)

    with open(apk, "rb") as f:
        data = f.read()
    url = "https://uploads.github.com/repos/%s/releases/%s/assets?name=%s" % (
        gr.REPO, release["id"], name,
    )
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Content-Type", "application/vnd.android.package-archive")
    with urllib.request.urlopen(req) as r:
        print("附件上传完成: %s (HTTP %s)" % (name, r.status))


def main():
    global TOKEN
    socket.setdefaulttimeout(120)

    args = [a for a in sys.argv[1:]]
    no_apk = "--no-apk" in args
    args = [a for a in args if not a.startswith("--")]
    tag = args[0] if args else ("v" + gr.read_version())

    TOKEN = gr.get_token()
    release = find_release(tag)
    print("命中 Release: %s (id=%s)" % (release["html_url"], release["id"]))

    notes = gr.read_notes_override(tag)
    if notes:
        api(
            "/repos/%s/releases/%s" % (gr.REPO, release["id"]),
            {"body": notes},
            method="PATCH",
        )
        print("说明正文已更新（来源 docs/release-notes/%s.md）" % tag)
    else:
        print("未找到 docs/release-notes/%s.md，说明正文保持不变" % tag)

    if not no_apk:
        version = tag.lstrip("v")
        apk = os.path.join(ROOT, "TimestampRecorder_v%s.apk" % version)
        if not os.path.exists(apk):
            sys.exit("找不到 APK：%s（加 --no-apk 可跳过附件上传）" % apk)
        gr.verify_release_signature(apk)
        upload_asset(release, apk)

    print("DONE=" + release["html_url"])


if __name__ == "__main__":
    main()
