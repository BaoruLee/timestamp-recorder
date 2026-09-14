#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 Android 自适应图标（Adaptive Icon）全套资源。

用法:
    python tools/make_adaptive_icon.py <前景图.png> [背景图.png 或 "#RRGGBB"]

前景: 正方形、带 alpha 的 PNG（画布 1024 最佳，更大也行）。
背景: 纯色给 "#RRGGBB"（只写进 colors.xml，不出位图）；也可给不透明方图。

做的事:
  1. 按 alpha 包围盒裁出主体
  2. 等比缩放到安全区（72dp 可视区再留 3% 余量），居中贴回正方形画布
  3. 输出 5 档密度的自适应前景位图
  4. 输出 5 档密度的传统合成图标（供 API 24~25 兜底，带圆角）
  5. 生成 mipmap-anydpi-v26/ic_launcher.xml 与 ic_launcher_round.xml
"""
import os
import sys

from PIL import Image, ImageDraw

CANVAS = 1024                    # 设计画布边长 px
SAFE_RATIO = 72 / 108            # 72dp 可视区占 108dp 画布
SAFE_MARGIN = 0.97               # 再留 3% 余量，避免圆形遮罩蹭到边缘
ADAPTIVE_SIZES = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
LEGACY_SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
LEGACY_RADIUS = 0.22             # 传统图标圆角比例（旧 launcher 未必自己裁）
LEGACY_FILL = 0.90               # 传统图标里主体占画布比例

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')


def alpha_bbox(img, threshold=8):
    """返回 alpha > threshold 的包围盒 (l, t, r, b)，闭区间语义。"""
    bbox = img.getchannel('A').point(lambda v: 255 if v > threshold else 0).getbbox()
    return bbox


def fit_content(fg, canvas=CANVAS, ratio=SAFE_RATIO, margin=SAFE_MARGIN):
    """裁出主体并等比缩放居中，返回 canvas×canvas 的 RGBA 图。"""
    box = alpha_bbox(fg)
    if box is None:
        raise SystemExit('前景图没有任何不透明像素，检查 alpha 通道')
    content = fg.crop(box)

    limit = canvas * ratio * margin
    scale = limit / max(content.width, content.height)
    new_w = max(1, round(content.width * scale))
    new_h = max(1, round(content.height * scale))
    content = content.resize((new_w, new_h), Image.LANCZOS)

    out = Image.new('RGBA', (canvas, canvas), (0, 0, 0, 0))
    out.paste(content, ((canvas - new_w) // 2, (canvas - new_h) // 2), content)
    return out


def rounded_mask(size, radius_ratio=LEGACY_RADIUS, ss=4):
    """4x 超采样画圆角矩形，缩放回来即得抗锯齿蒙版。"""
    big = size * ss
    mask = Image.new('L', (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, big - 1, big - 1], radius=int(big * radius_ratio), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def parse_bg(arg):
    """返回 (mode, value)：mode 为 'color' 时 value 是 (r,g,b)；为 'image' 时是 RGB 图。"""
    if arg is None:
        return 'color', (20, 48, 107)
    s = arg.strip()
    if s.startswith('#') or (len(s) == 6 and all(c in '0123456789abcdefABCDEF' for c in s)):
        h = s.lstrip('#')
        return 'color', tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    img = Image.open(s)
    px = img.convert('RGB')
    if px.getextrema() != ((px.getpixel((0, 0))[0],) * 2,
                           (px.getpixel((0, 0))[1],) * 2,
                           (px.getpixel((0, 0))[2],) * 2):
        return 'image', px
    return 'color', px.getpixel((0, 0))


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)

    fg_raw = Image.open(sys.argv[1]).convert('RGBA')
    bg_mode, bg = parse_bg(sys.argv[2] if len(sys.argv) > 2 else None)

    if fg_raw.width != fg_raw.height:
        print('提示: 前景不是正方形（%dx%d），按短边中心裁切' % fg_raw.size)
        s = min(fg_raw.size)
        l = (fg_raw.width - s) // 2
        t = (fg_raw.height - s) // 2
        fg_raw = fg_raw.crop((l, t, l + s, t + s))

    fg = fit_content(fg_raw)
    box = alpha_bbox(fg_raw)
    print('主体包围盒: %s -> 缩放居中后占安全区' % (box,))

    # 背景层（位图模式时烘焙成 drawable）
    if bg_mode == 'image':
        bg_canvas = bg.resize((CANVAS, CANVAS), Image.LANCZOS).convert('RGBA')
        bg_dir = os.path.join(RES, 'drawable-nodpi')
        os.makedirs(bg_dir, exist_ok=True)
        bg_canvas.resize((432, 432), Image.LANCZOS).save(
            os.path.join(bg_dir, 'ic_launcher_background.png'))
        print('背景: 位图 -> drawable-nodpi/ic_launcher_background.png')
    else:
        print('背景: 纯色 #%02X%02X%02X -> colors.xml' % bg)

    # 自适应前景 + 传统合成图
    for dens, size in ADAPTIVE_SIZES.items():
        d = os.path.join(RES, 'mipmap-' + dens)
        os.makedirs(d, exist_ok=True)
        fg.resize((size, size), Image.LANCZOS).save(
            os.path.join(d, 'ic_launcher_foreground.png'))

        legacy = LEGACY_SIZES[dens]
        base = Image.new('RGBA', (CANVAS, CANVAS), bg + (255,) if bg_mode == 'color'
                         else (0, 0, 0, 0))
        if bg_mode == 'image':
            base.paste(bg.resize((CANVAS, CANVAS), Image.LANCZOS), (0, 0))
        inner = round(legacy * LEGACY_FILL)
        content = fg_raw.crop(box)
        sc = inner / max(content.width, content.height)
        content = content.resize((max(1, round(content.width * sc)),
                                  max(1, round(content.height * sc))), Image.LANCZOS)
        small = Image.new('RGBA', (legacy, legacy), (0, 0, 0, 0))
        small.paste(content, ((legacy - content.width) // 2,
                              (legacy - content.height) // 2), content)
        small.putalpha(rounded_mask(legacy))
        small.save(os.path.join(d, 'ic_launcher.png'))

    # anydpi-v26 自适应图标
    anydpi = os.path.join(RES, 'mipmap-anydpi-v26')
    os.makedirs(anydpi, exist_ok=True)
    bg_ref = ('@drawable/ic_launcher_background' if bg_mode == 'image'
              else '@color/ic_launcher_background')
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="%s" />\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
           '</adaptive-icon>\n' % bg_ref)
    for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        with open(os.path.join(anydpi, name), 'w', encoding='utf-8', newline='\n') as f:
            f.write(xml)

    print('已生成: %d 档自适应前景 + %d 档传统图标 + anydpi-v26 XML'
          % (len(ADAPTIVE_SIZES), len(LEGACY_SIZES)))
    if bg_mode == 'color':
        print('记得在 values/colors.xml 写入 ic_launcher_background = #%02X%02X%02X' % bg)


if __name__ == '__main__':
    main()
