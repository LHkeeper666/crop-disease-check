"""在图片上绘制检测框。病害=红色, 虫害=蓝色, 合并为一张标注图。"""

from __future__ import annotations

import logging
from typing import List, Tuple

import cv2
import numpy as np

from schemas import DetectionItem

logger = logging.getLogger(__name__)

# 框颜色 (BGR)
DISEASE_COLOR = (68, 68, 255)  # 红色
PEST_COLOR = (255, 136, 68)  # 蓝色

# 标注样式
BOX_THICKNESS = 2
FONT = cv2.FONT_HERSHEY_SIMPLEX
FONT_SCALE = 0.55
FONT_THICKNESS = 1
LABEL_PADDING = 4


def _draw_single_box(
    canvas: np.ndarray,
    det: DetectionItem,
    color: Tuple[int, int, int],
    prefix: str,
) -> None:
    """在 canvas 上绘制单个检测框 + 标签"""
    x, y, w, h = det.bbox.x, det.bbox.y, det.bbox.width, det.bbox.height

    # 框
    cv2.rectangle(canvas, (x, y), (x + w, y + h), color, BOX_THICKNESS)

    # 标签文本
    label = f"{prefix}{det.name_cn or det.class_name} {det.confidence:.2f}"
    (tw, th), baseline = cv2.getTextSize(label, FONT, FONT_SCALE, FONT_THICKNESS)

    # 标签背景（框上方）
    label_y0 = max(y - th - baseline - LABEL_PADDING * 2, 0)
    label_x1 = x + tw + LABEL_PADDING * 2
    cv2.rectangle(canvas, (x, label_y0), (label_x1, y), color, -1)

    # 标签文字（白色）
    text_y = y - baseline - LABEL_PADDING
    if text_y < th:
        text_y = y + th + baseline + LABEL_PADDING  # 框上方放不下则放框内顶部
    cv2.putText(canvas, label, (x + LABEL_PADDING, text_y),
                FONT, FONT_SCALE, (255, 255, 255), FONT_THICKNESS, cv2.LINE_AA)


def draw_annotated_image(
    image_bgr: np.ndarray,
    disease_detections: List[DetectionItem],
    pest_detections: List[DetectionItem],
) -> np.ndarray:
    """在图像副本上绘制病害+虫害检测框，返回标注图 (BGR)"""
    canvas = image_bgr.copy()

    for det in disease_detections:
        _draw_single_box(canvas, det, DISEASE_COLOR, "[病] ")

    for det in pest_detections:
        _draw_single_box(canvas, det, PEST_COLOR, "[虫] ")

    return canvas


def encode_image_to_jpeg(image_bgr: np.ndarray, quality: int = 85) -> bytes:
    """将 BGR 图像编码为 JPEG 字节"""
    success, encoded = cv2.imencode(".jpg", image_bgr,
                                     [cv2.IMWRITE_JPEG_QUALITY, quality])
    if not success:
        raise RuntimeError("JPEG 编码失败")
    return encoded.tobytes()
