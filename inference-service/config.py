import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent

# ---- 模型路径 ----
DISEASE_MODEL_PATH = os.getenv(
    "DISEASE_MODEL_PATH",
    str(PROJECT_ROOT / "models" / "disease" / "best.pt"),
)
PEST_MODEL_PATH = os.getenv(
    "PEST_MODEL_PATH",
    str(PROJECT_ROOT / "models" / "pest" / "best.pt"),
)

# ---- 推理参数 ----
DEFAULT_CONFIDENCE = float(os.getenv("DEFAULT_CONFIDENCE", "0.5"))
MAX_IMAGE_SIZE_MB = int(os.getenv("MAX_IMAGE_SIZE_MB", "10"))
MAX_BATCH_SIZE = int(os.getenv("MAX_BATCH_SIZE", "20"))
DEVICE = os.getenv("DEVICE", "auto")  # auto / cpu / cuda:0

# ---- 输出目录 ----
OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", str(PROJECT_ROOT / "outputs")))
ANNOTATED_DIR = OUTPUT_DIR / "annotated"
ANNOTATED_DIR.mkdir(parents=True, exist_ok=True)

# ---- 标注图参数 ----
ANNOTATED_JPEG_QUALITY = int(os.getenv("ANNOTATED_JPEG_QUALITY", "85"))

# ---- 服务 ----
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8000"))

# ---- 中文类名映射文件 ----
CLASS_NAMES_ZH_PATH = BASE_DIR / "class_names_zh.json"

# ---- MinIO 对象存储 ----
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "agri_minio_admin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "agri_minio_2026")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "agri-monitor")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"
