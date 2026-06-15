"""模型管理器：加载、推理、并发控制。"""

from __future__ import annotations

import asyncio
import base64
import json
import logging
import time
import uuid
from io import BytesIO
from typing import List, Optional, Tuple

import cv2
import httpx
import numpy as np
from PIL import Image
from ultralytics import YOLO

import config
from schemas import BBox, DetectionItem, ImageInfo

logger = logging.getLogger(__name__)


def _load_name_mapping() -> dict:
    """加载中文类名映射（仅病害模型需要，虫害模型类名已是中文）"""
    if config.CLASS_NAMES_ZH_PATH.exists():
        with open(config.CLASS_NAMES_ZH_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data.get("disease", {})
    return {}


class ModelManager:
    """单例模型管理器。启动时加载两个 YOLO 模型，推理时串行执行。"""

    def __init__(self) -> None:
        self._disease_model: Optional[YOLO] = None
        self._pest_model: Optional[YOLO] = None
        self._lock = asyncio.Lock()
        self._disease_names: dict = {}
        self._pest_names: dict = {}
        self._zh_map: dict = {}
        self._loaded = False

    # ------------------------------------------------------------------
    #  加载
    # ------------------------------------------------------------------

    def load(self) -> None:
        logger.info("加载病害模型: %s", config.DISEASE_MODEL_PATH)
        self._disease_model = YOLO(config.DISEASE_MODEL_PATH)
        self._disease_names = dict(self._disease_model.names)  # {int: str}

        logger.info("加载虫害模型: %s", config.PEST_MODEL_PATH)
        self._pest_model = YOLO(config.PEST_MODEL_PATH)
        self._pest_names = dict(self._pest_model.names)

        self._zh_map = _load_name_mapping()
        self._loaded = True
        logger.info("模型加载完成 (病害 %d 类, 虫害 %d 类)",
                     len(self._disease_names), len(self._pest_names))

    @property
    def loaded(self) -> bool:
        return self._loaded

    @property
    def queue_depth(self) -> int:
        """估算当前排队请求数（0 = 空闲，>0 = 有请求在等待或执行中）"""
        return 1 if (self._lock.locked()) else 0

    # ------------------------------------------------------------------
    #  图片解码（支持 base64 / url）
    # ------------------------------------------------------------------

    async def _load_image(self, image_input) -> Tuple[np.ndarray, ImageInfo]:
        """将请求中的图片转为 BGR numpy array。支持 base64 和 url 两种方式。"""
        raw_bytes: bytes

        if image_input.type == "base64":
            raw_bytes = base64.b64decode(image_input.data)
        else:
            raw_bytes = await self._download(image_input.data)

        # 转换为 OpenCV BGR
        pil_img = Image.open(BytesIO(raw_bytes))
        if pil_img.mode in ("RGBA", "P"):
            pil_img = pil_img.convert("RGB")
        image_bgr = cv2.cvtColor(np.array(pil_img), cv2.COLOR_RGB2BGR)

        info = ImageInfo(
            width=pil_img.width,
            height=pil_img.height,
            format=pil_img.format or "UNKNOWN",
        )
        return image_bgr, info

    @staticmethod
    async def _download(url: str) -> bytes:
        """异步下载图片，带超时和大小限制"""
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            content = resp.read()
            max_bytes = config.MAX_IMAGE_SIZE_MB * 1024 * 1024
            if len(content) > max_bytes:
                raise ValueError(f"图片过大: {len(content) / 1024 / 1024:.1f}MB "
                                 f"(上限 {config.MAX_IMAGE_SIZE_MB}MB)")
            return content

    # ------------------------------------------------------------------
    #  推理核心
    # ------------------------------------------------------------------

    def _run_model(self, model: YOLO, image_bgr: np.ndarray,
                   confidence: float) -> Tuple[List[DetectionItem], float]:
        """同步执行单个模型推理，返回检测列表 + 耗时(ms)。在 executor 线程中调用。"""
        t0 = time.perf_counter()
        results = model(image_bgr, conf=confidence, verbose=False, device=config.DEVICE)
        elapsed = (time.perf_counter() - t0) * 1000

        detections: List[DetectionItem] = []
        # results 是 list[ultralytics.engine.results.Results]
        for r in results:
            if r.boxes is None:
                continue
            names = r.names  # {int: str}
            for box in r.boxes:
                cls_id = int(box.cls)
                cls_name_raw = names.get(cls_id, f"class_{cls_id}").strip()
                conf = float(box.conf)
                xyxy = box.xyxy.cpu().numpy()[0]

                detections.append(DetectionItem(
                    class_id=cls_id,
                    class_name=cls_name_raw,
                    name_cn=self._zh_map.get(cls_name_raw, cls_name_raw),
                    confidence=round(conf, 4),
                    bbox=BBox(
                        x=int(xyxy[0]), y=int(xyxy[1]),
                        width=int(xyxy[2] - xyxy[0]),
                        height=int(xyxy[3] - xyxy[1]),
                    ),
                ))
        return detections, round(elapsed, 1)

    async def infer(self, image_bgr: np.ndarray,
                    confidence: float) -> Tuple[List[DetectionItem], List[DetectionItem],
                                                float, float, float]:
        """
        执行 病害→虫害 顺序推理。asyncio.Lock 保证同一时刻只有一个请求在执行。
        推理本身通过 run_in_executor 扔到线程池，不阻塞事件循环。
        返回: (disease_detections, pest_detections,
                disease_elapsed_ms, pest_elapsed_ms, total_elapsed_ms)
        """
        async with self._lock:
            loop = asyncio.get_running_loop()

            disease_dets, disease_time = await loop.run_in_executor(
                None, self._run_model, self._disease_model, image_bgr, confidence,
            )

            pest_dets, pest_time = await loop.run_in_executor(
                None, self._run_model, self._pest_model, image_bgr, confidence,
            )

        total = disease_time + pest_time
        logger.debug("推理完成: 病害 %d 个 (%.1fms), 虫害 %d 个 (%.1fms), 合计 %.1fms",
                      len(disease_dets), disease_time, len(pest_dets), pest_time, total)
        return disease_dets, pest_dets, disease_time, pest_time, round(total, 1)

    # ------------------------------------------------------------------
    #  单张图片完整处理流程
    # ------------------------------------------------------------------

    # ---- 中文名补丁（虫害模型） ----
    def _patch_pest_cn(self, det: DetectionItem) -> None:
        """虫害模型类名已是中文，直接复用为 name_cn"""
        if not det.name_cn or det.name_cn == det.class_name:
            det.name_cn = det.class_name

    async def process_one(
        self, image_input, confidence: float,
        return_annotated: bool = True,
    ):
        """单张图片完整处理：解码 → 推理 → 标注 → 持久化。
        返回: (disease_dets, pest_dets, disease_ms, pest_ms,
                annotated_path, annotated_url, img_info)
        """
        image_bgr, img_info = await self._load_image(image_input)
        disease_dets, pest_dets, disease_ms, pest_ms, _ = \
            await self.infer(image_bgr, confidence)

        annotated_path: Optional[str] = None
        annotated_url: Optional[str] = None

        if return_annotated and (disease_dets or pest_dets):
            from annotator import draw_annotated_image, encode_image_to_jpeg
            from storage import upload_bytes

            annotated_bgr = draw_annotated_image(image_bgr, disease_dets, pest_dets)
            jpeg_bytes = encode_image_to_jpeg(annotated_bgr, config.ANNOTATED_JPEG_QUALITY)

            filename = f"{uuid.uuid4().hex[:8]}_annotated.jpg"

            # 存本地
            save_path = config.ANNOTATED_DIR / filename
            save_path.write_bytes(jpeg_bytes)
            annotated_path = str(save_path)

            # 上传 MinIO
            try:
                object_name = f"annotated/{filename}"
                annotated_url = upload_bytes(jpeg_bytes, object_name)
            except Exception as exc:
                logger.warning("MinIO 上传失败（不影响本地存储）: %s", exc)

        # 虫害中文名
        for d in pest_dets:
            self._patch_pest_cn(d)

        return disease_dets, pest_dets, disease_ms, pest_ms, \
            annotated_path, annotated_url, img_info


# ============================================================
#  全局单例
# ============================================================

model_manager = ModelManager()
