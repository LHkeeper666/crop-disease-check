"""在图片上绘制检测框。病害=红色, 虫害=蓝色, 合并为一张标注图。"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import List, Tuple

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from schemas import DetectionItem

logger = logging.getLogger(__name__)

# 框颜色 (BGR)
DISEASE_COLOR = (68, 68, 255)  # 红色
PEST_COLOR = (255, 136, 68)  # 蓝色

# 标注样式
BOX_THICKNESS = 2
LABEL_PADDING = 4

# 中文字体路径（Windows/Linux 通用）
_FONT_PATHS = [
    # Windows
    "C:/Windows/Fonts/msyh.ttc",    # 微软雅黑
    "C:/Windows/Fonts/simhei.ttf",   # 黑体
    "C:/Windows/Fonts/simsun.ttc",   # 宋体
    # Linux
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
]

def _get_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    """加载中文字体，找不到则回退到 PIL 默认字体"""
    for fp in _FONT_PATHS:
        if os.path.exists(fp):
            try:
                return ImageFont.truetype(fp, size)
            except Exception:
                continue
    logger.warning("未找到中文字体，标注可能显示为方块")
    return ImageFont.load_default()


def _draw_single_box(
    canvas: np.ndarray,
    det: DetectionItem,
    color: Tuple[int, int, int],
    prefix: str,
    font: ImageFont.FreeTypeFont | ImageFont.ImageFont,
) -> None:
    """在 canvas 上绘制单个检测框 + 标签（使用 PIL 绘制以支持中文）"""
    x, y, w, h = det.bbox.x, det.bbox.y, det.bbox.width, det.bbox.height

    # 绘制检测框（OpenCV 画矩形没问题）
    cv2.rectangle(canvas, (x, y), (x + w, y + h), color, BOX_THICKNESS)

    # 标签文本
    label = f"{prefix}{det.name_cn or det.class_name} {det.confidence:.2f}"

    # 使用 PIL 测量文本尺寸
    pil_img = Image.fromarray(cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB))
    draw = ImageDraw.Draw(pil_img)
    bbox = draw.textbbox((0, 0), label, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    baseline = th // 4

    # 标签背景（框上方）
    label_y0 = max(y - th - baseline - LABEL_PADDING * 2, 0)
    label_x1 = x + tw + LABEL_PADDING * 2
    cv2.rectangle(canvas, (x, label_y0), (label_x1, y), color, -1)

    # 标签文字（白色，使用 PIL 绘制中文）
    text_y = y - baseline - LABEL_PADDING
    if text_y < th:
        text_y = y + th + baseline + LABEL_PADDING

    draw.text((x + LABEL_PADDING, text_y - th), label, font=font, fill=(255, 255, 255))
    canvas[:] = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)


def draw_annotated_image(
    image_bgr: np.ndarray,
    disease_detections: List[DetectionItem],
    pest_detections: List[DetectionItem],
) -> np.ndarray:
    """在图像副本上绘制病害+虫害检测框，返回标注图 (BGR)"""
    canvas = image_bgr.copy()

    # 根据图片高度计算字体大小
    font_size = max(16, image_bgr.shape[0] // 25)
    font = _get_font(font_size)

    for det in disease_detections:
        _draw_single_box(canvas, det, DISEASE_COLOR, "[病] ", font)

    for det in pest_detections:
        _draw_single_box(canvas, det, PEST_COLOR, "[虫] ", font)

    return canvas


def encode_image_to_jpeg(image_bgr: np.ndarray, quality: int = 85) -> bytes:
    """将 BGR 图像编码为 JPEG 字节"""
    success, encoded = cv2.imencode(".jpg", image_bgr,
                                     [cv2.IMWRITE_JPEG_QUALITY, quality])
    if not success:
        raise RuntimeError("JPEG 编码失败")
    return encoded.tobytes()
