# -*- coding: utf-8 -*-
"""
生成测试用 mock 图表图片（模拟真实图表样式，不包含任何业务数据）
"""
import os
import math

from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.join(os.path.dirname(__file__), '..', 'resources', 'images')
WIDTH, HEIGHT = 500, 300
BG = (255, 255, 255)
AXIS_COLOR = (80, 80, 80)
GRID_COLOR = (230, 230, 230)
LABEL_COLOR = (100, 100, 100)
TITLE_COLOR = (50, 50, 50)

PALETTE = [
    (70, 130, 180),
    (60, 179, 113),
    (205, 92, 92),
    (147, 112, 219),
    (255, 165, 0),
]

try:
    FONT = ImageFont.truetype("arial.ttf", 14)
    FONT_TITLE = ImageFont.truetype("arial.ttf", 18)
    FONT_SMALL = ImageFont.truetype("arial.ttf", 11)
except OSError:
    FONT = ImageFont.load_default()
    FONT_TITLE = ImageFont.load_default()
    FONT_SMALL = ImageFont.load_default()


def _draw_title(draw, title):
    bbox = draw.textbbox((0, 0), title, font=FONT_TITLE)
    tw = bbox[2] - bbox[0]
    draw.text(((WIDTH - tw) // 2, 12), title, fill=TITLE_COLOR, font=FONT_TITLE)


def _draw_axes(draw, margin):
    # Y axis
    draw.line([(margin, 40), (margin, HEIGHT - 30)], fill=AXIS_COLOR, width=1)
    # X axis
    draw.line([(margin, HEIGHT - 30), (WIDTH - 20, HEIGHT - 30)], fill=AXIS_COLOR, width=1)


def _draw_grid_lines(draw, margin, y_max, steps=5):
    for i in range(1, steps + 1):
        y = HEIGHT - 30 - int((HEIGHT - 70) * i / steps)
        draw.line([(margin, y), (WIDTH - 20, y)], fill=GRID_COLOR, width=1)
        val = int(y_max * i / steps)
        draw.text((5, y - 8), str(val), fill=LABEL_COLOR, font=FONT_SMALL)


def draw_bar_chart(path, title, categories, values):
    img = Image.new('RGB', (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(img)

    _draw_title(draw, title)
    margin = 50
    _draw_axes(draw, margin)

    y_max = max(values) * 1.2
    _draw_grid_lines(draw, margin, y_max)

    n = len(categories)
    bar_area = WIDTH - margin - 30
    bar_w = max(10, bar_area // (n * 2))
    gap = (bar_area - bar_w * n) // (n + 1)

    for i, (cat, val) in enumerate(zip(categories, values)):
        x = margin + gap + i * (bar_w + gap)
        h = int((HEIGHT - 70) * val / y_max)
        y = HEIGHT - 30 - h
        color = PALETTE[i % len(PALETTE)]
        draw.rectangle([x, y, x + bar_w, HEIGHT - 30], fill=color)
        # label
        bbox = draw.textbbox((0, 0), cat, font=FONT_SMALL)
        tw = bbox[2] - bbox[0]
        draw.text((x + (bar_w - tw) // 2, HEIGHT - 26), cat, fill=LABEL_COLOR, font=FONT_SMALL)
        # value
        val_text = str(val)
        bbox2 = draw.textbbox((0, 0), val_text, font=FONT_SMALL)
        tw2 = bbox2[2] - bbox2[0]
        draw.text((x + (bar_w - tw2) // 2, y - 16), val_text, fill=LABEL_COLOR, font=FONT_SMALL)

    img.save(path, 'PNG')
    print(f'Saved: {path}')


def draw_pie_chart(path, title, labels, values):
    img = Image.new('RGB', (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(img)

    _draw_title(draw, title)

    total = sum(values)
    cx, cy, r = 180, 165, 100
    start = -90

    for i, (label, val) in enumerate(zip(labels, values)):
        angle = 360 * val / total
        draw.pieslice([cx - r, cy - r, cx + r, cy + r], start, start + angle,
                       fill=PALETTE[i % len(PALETTE)], outline=BG, width=2)
        start += angle

    # legend
    ly = 55
    for i, (label, val) in enumerate(zip(labels, values)):
        color = PALETTE[i % len(PALETTE)]
        lx = 320
        draw.rectangle([lx, ly, lx + 14, ly + 14], fill=color)
        pct = val * 100 / total
        draw.text((lx + 20, ly - 1), f'{label} ({pct:.0f}%)', fill=LABEL_COLOR, font=FONT)
        ly += 22

    img.save(path, 'PNG')
    print(f'Saved: {path}')


def draw_line_chart(path, title, x_labels, datasets):
    img = Image.new('RGB', (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(img)

    _draw_title(draw, title)
    margin = 50
    _draw_axes(draw, margin)

    all_vals = [v for ds in datasets for _, v in ds]
    y_max = max(all_vals) * 1.2
    _draw_grid_lines(draw, margin, y_max)

    n = len(x_labels)
    x_step = (WIDTH - margin - 30) / max(n - 1, 1)

    # x labels
    for i, lbl in enumerate(x_labels):
        x = margin + int(i * x_step)
        draw.text((x - 5, HEIGHT - 26), lbl, fill=LABEL_COLOR, font=FONT_SMALL)

    for di, ds in enumerate(datasets):
        color = PALETTE[di % len(PALETTE)]
        points = []
        for i, (_, val) in enumerate(ds):
            x = margin + int(i * x_step)
            y = HEIGHT - 30 - int((HEIGHT - 70) * val / y_max)
            points.append((x, y))
        if len(points) >= 2:
            draw.line(points, fill=color, width=2)
        for p in points:
            draw.ellipse([p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3], fill=color)

    # legend
    ly = 45
    for di, ds in enumerate(datasets):
        color = PALETTE[di % len(PALETTE)]
        lx = WIDTH - 80
        draw.rectangle([lx, ly, lx + 14, ly + 14], fill=color)
        ly += 22

    img.save(path, 'PNG')
    print(f'Saved: {path}')


if __name__ == '__main__':
    os.makedirs(OUT_DIR, exist_ok=True)

    # chart1: 外联告警态势 - 柱图
    draw_bar_chart(
        os.path.join(OUT_DIR, 'chart1.png'),
        'Outbound Alert Trend',
        ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
        [120, 85, 150, 200, 170, 90, 60],
    )

    # chart2: 正面攻击态势 - 饼图
    draw_pie_chart(
        os.path.join(OUT_DIR, 'chart2.png'),
        'Inbound Attack Distribution',
        ['SQLi', 'XSS', 'RCE', 'Scan', 'Other'],
        [35, 20, 15, 20, 10],
    )

    # chart3: 单位外联趋势 - 折线图
    draw_line_chart(
        os.path.join(OUT_DIR, 'chart3.png'),
        'Unit Outbound Trend',
        ['W1', 'W2', 'W3', 'W4', 'W5', 'W6'],
        [
            [('A', 800), ('B', 650), ('C', 900), ('D', 750), ('E', 1100), ('F', 950)],
        ],
    )

    # chart4: 单位攻击趋势 - 柱图
    draw_bar_chart(
        os.path.join(OUT_DIR, 'chart4.png'),
        'Unit Inbound Trend',
        ['W1', 'W2', 'W3', 'W4'],
        [50000, 67000, 45000, 178000],
    )

    print('Done')
