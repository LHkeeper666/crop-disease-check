from __future__ import annotations

from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field, field_validator


# ============================================================
#  请求模型
# ============================================================

class ImageInput(BaseModel):
    """单张图片输入，支持 url 或 base64 两种方式"""

    type: str = Field(..., description="图片传入方式: url / base64")
    data: str = Field(..., description="OSS 地址 或 base64 编码字符串")

    @field_validator("type")
    @classmethod
    def _check_type(cls, v: str) -> str:
        if v not in ("url", "base64"):
            raise ValueError("type 必须为 url 或 base64")
        return v


class DetectRequest(BaseModel):
    """单张图片推理请求"""

    image: ImageInput = Field(..., description="待检测图片")
    confidence: float = Field(0.5, ge=0.1, le=1.0, description="置信度阈值")
    return_annotated: bool = Field(True, description="是否生成标注图并上传存储")


class BatchDetectRequest(BaseModel):
    """批量图片推理请求"""

    images: List[ImageInput] = Field(
        ..., min_length=1, max_length=20, description="待检测图片列表 (1-20)"
    )
    confidence: float = Field(0.5, ge=0.1, le=1.0, description="置信度阈值")
    return_annotated: bool = Field(True, description="是否生成标注图并上传存储")


# ============================================================
#  响应模型 —— 匹配 Java 后端 Result<T> 结构
# ============================================================

class BBox(BaseModel):
    x: int
    y: int
    width: int
    height: int


class DetectionItem(BaseModel):
    class_id: int = Field(..., description="模型 class index，对应 disease_info.id 或 pest_info.id")
    class_name: str = Field(..., description="类别英文名 / 原始名")
    name_cn: str = Field("", description="类别中文名")
    confidence: float = Field(..., description="置信度 [0, 1]")
    bbox: BBox = Field(..., description="边界框 (左上角坐标 + 宽高)")


class ModelResult(BaseModel):
    detections: List[DetectionItem] = Field(default_factory=list, description="检出列表")
    count: int = Field(0, description="检出数量")
    elapsed_ms: float = Field(0.0, description="本模型推理耗时(ms)")


class ImageInfo(BaseModel):
    width: int
    height: int
    format: str


class SingleDetectData(BaseModel):
    """单张图片推理的 data 载荷"""

    disease: ModelResult = Field(default_factory=ModelResult)
    pest: ModelResult = Field(default_factory=ModelResult)
    annotated_path: Optional[str] = Field(None, description="标注图本地存储路径")
    annotated_url: Optional[str] = Field(None, description="标注图 MinIO 访问 URL")
    total_elapsed_ms: float = Field(0.0, description="总耗时(ms)")
    image_info: Optional[ImageInfo] = None


class BatchImageResult(BaseModel):
    """批量推理中每张图片的结果"""

    image_index: int
    disease: ModelResult = Field(default_factory=ModelResult)
    pest: ModelResult = Field(default_factory=ModelResult)
    annotated_path: Optional[str] = None
    annotated_url: Optional[str] = None
    image_info: Optional[ImageInfo] = None
    error: Optional[str] = None


class BatchDetectData(BaseModel):
    """批量推理的 data 载荷"""

    results: List[BatchImageResult] = Field(default_factory=list)
    total_elapsed_ms: float = 0.0
    success_count: int = 0
    fail_count: int = 0


class HealthData(BaseModel):
    status: str
    models: Dict[str, Any]
    gpu: Dict[str, Any]
    queue_depth: int


# ---- 统一响应外壳 (匹配 Java Result<T>) ----

class DetectResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: Optional[SingleDetectData] = None


class BatchDetectResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: Optional[BatchDetectData] = None


class ErrorResponse(BaseModel):
    code: int
    message: str
    data: None = None


class HealthResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: HealthData
