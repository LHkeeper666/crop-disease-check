"""
农作物病害 / 虫害推理服务 (FastAPI)

启动: uvicorn main:app --host 0.0.0.0 --port 8000

端点:
  POST /api/v1/detect       单张图片推理
  POST /api/v1/detect/batch 批量图片推理 (≤20)
  GET  /api/v1/health       服务健康检查
"""

from __future__ import annotations

import asyncio
import logging
import time
import traceback
from contextlib import asynccontextmanager

import httpx
import torch
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

import config
from predictor import model_manager
from schemas import (
    BatchDetectData,
    BatchDetectRequest,
    BatchDetectResponse,
    BatchImageResult,
    DetectRequest,
    DetectResponse,
    ErrorResponse,
    HealthData,
    HealthResponse,
    ModelResult,
    SingleDetectData,
)

# ---------------------------------------------------------------------------
#  日志
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("inference_service")


# ---------------------------------------------------------------------------
#  生命周期
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时加载模型，关闭时释放资源"""
    logger.info("服务启动中...")
    loop = asyncio.get_running_loop()
    await loop.run_in_executor(None, model_manager.load)

    # 初始化 MinIO 桶
    try:
        from storage import ensure_bucket
        await loop.run_in_executor(None, ensure_bucket)
    except Exception as exc:
        logger.warning("MinIO 初始化失败（本地存储仍可用）: %s", exc)

    logger.info("服务就绪，端口 %d", config.PORT)
    yield
    logger.info("服务关闭")


app = FastAPI(
    title="农作物病虫害推理服务",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS
# TODO(waiwai9000):可能需要配置cors,防范恶意访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------------------------
#  异常处理
# ---------------------------------------------------------------------------

@app.exception_handler(HTTPException)
async def http_exception_handler(_request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content=ErrorResponse(code=exc.status_code, message=exc.detail).model_dump(),
    )


@app.exception_handler(Exception)
async def general_exception_handler(_request: Request, exc: Exception):
    logger.error("未捕获异常: %s", traceback.format_exc())
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(code=500, message=f"服务内部错误: {exc}").model_dump(),
    )


# ---------------------------------------------------------------------------
#  辅助函数
# ---------------------------------------------------------------------------

def _check_ready() -> None:
    if not model_manager.loaded:
        raise HTTPException(status_code=503, detail="模型尚未加载完成，请稍后重试")


# ---------------------------------------------------------------------------
#  POST /api/v1/detect — 单张图片推理
# ---------------------------------------------------------------------------

@app.post("/api/v1/detect", response_model=DetectResponse)
async def detect_single(req: DetectRequest):
    """
    单张图片推理，同时执行病害 + 虫害检测。

    请求体示例:

        {
          "image": { "type": "url", "data": "https://oss.example.com/crop.jpg" },
          "confidence": 0.5
        }

    或 base64 方式:

        {
          "image": { "type": "base64", "data": "/9j/4AAQSkZJRg..." },
          "confidence": 0.5
        }
    """
    _check_ready()
    t0 = time.perf_counter()

    try:
        (disease_dets, pest_dets, disease_ms, pest_ms,
         anno_path, anno_url, img_info) = await model_manager.process_one(
            req.image, req.confidence, req.return_annotated,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"图片下载失败: {exc}")

    total_elapsed = round((time.perf_counter() - t0) * 1000, 1)

    return DetectResponse(
        data=SingleDetectData(
            disease=ModelResult(
                detections=disease_dets,
                count=len(disease_dets),
                elapsed_ms=disease_ms,
            ),
            pest=ModelResult(
                detections=pest_dets,
                count=len(pest_dets),
                elapsed_ms=pest_ms,
            ),
            annotated_path=anno_path,
            annotated_url=anno_url,
            total_elapsed_ms=total_elapsed,
            image_info=img_info,
        ),
    )


# ---------------------------------------------------------------------------
#  POST /api/v1/detect/batch — 批量图片推理
# ---------------------------------------------------------------------------

@app.post("/api/v1/detect/batch", response_model=BatchDetectResponse)
async def detect_batch(req: BatchDetectRequest):
    """
    批量图片推理（最多 20 张），部分失败不影响其他图片。

    请求体示例:

        {
          "images": [
            { "type": "url", "data": "https://oss.example.com/001.jpg" },
            { "type": "base64", "data": "/9j/4AAQSkZJRg..." }
          ],
          "confidence": 0.5
        }
    """
    _check_ready()
    t0 = time.perf_counter()

    results: list[BatchImageResult] = []
    success = fail = 0

    for idx, image_input in enumerate(req.images):
        try:
            disease_dets, pest_dets, disease_ms, pest_ms, \
                anno_path, anno_url, img_info = await model_manager.process_one(
                    image_input, req.confidence, req.return_annotated,
                )
            results.append(BatchImageResult(
                image_index=idx,
                disease=ModelResult(
                    detections=disease_dets,
                    count=len(disease_dets),
                    elapsed_ms=disease_ms,
                ),
                pest=ModelResult(
                    detections=pest_dets,
                    count=len(pest_dets),
                    elapsed_ms=pest_ms,
                ),
                annotated_path=anno_path,
                annotated_url=anno_url,
                image_info=img_info,
            ))
            success += 1

        except (ValueError, httpx.HTTPError) as exc:
            logger.warning("批量推理 [%d] 失败: %s", idx, exc)
            results.append(BatchImageResult(image_index=idx, error=str(exc)))
            fail += 1

        except Exception:
            logger.error("批量推理 [%d] 未知错误:\n%s", idx, traceback.format_exc())
            results.append(BatchImageResult(image_index=idx, error="推理服务内部错误"))
            fail += 1

    total_elapsed = round((time.perf_counter() - t0) * 1000, 1)

    return BatchDetectResponse(
        data=BatchDetectData(
            results=results,
            total_elapsed_ms=total_elapsed,
            success_count=success,
            fail_count=fail,
        ),
    )


# ---------------------------------------------------------------------------
#  GET /api/v1/health — 健康检查
# ---------------------------------------------------------------------------

@app.get("/api/v1/health", response_model=HealthResponse)
async def health():
    gpu_available = torch.cuda.is_available()
    gpu_info: dict = {"available": gpu_available}
    if gpu_available:
        gpu_info.update({
            "name": torch.cuda.get_device_name(0),
            "memory_total_mb": round(torch.cuda.get_device_properties(0).total_memory / 1024 ** 2),
            "memory_allocated_mb": round(torch.cuda.memory_allocated(0) / 1024 ** 2),
        })

    return HealthResponse(
        data=HealthData(
            status="ready" if model_manager.loaded else "loading",
            models={
                "disease": {"loaded": model_manager.loaded, "path": config.DISEASE_MODEL_PATH},
                "pest":    {"loaded": model_manager.loaded, "path": config.PEST_MODEL_PATH},
            },
            gpu=gpu_info,
            queue_depth=model_manager.queue_depth,
        ),
    )


# ---------------------------------------------------------------------------
#  直接启动
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=config.HOST, port=config.PORT, log_level="info")
