"""MinIO 对象存储客户端，负责桶初始化与文件上传。"""

from __future__ import annotations

import logging
from io import BytesIO
from typing import Optional

from minio import Minio
from minio.error import S3Error

import config

logger = logging.getLogger(__name__)

_client: Optional[Minio] = None


def get_client() -> Minio:
    """获取或创建 MinIO 客户端单例。"""
    global _client
    if _client is None:
        endpoint = config.MINIO_ENDPOINT
        # 去掉协议前缀，minio SDK 只接受 host:port
        if "://" in endpoint:
            endpoint = endpoint.split("://", 1)[1]

        _client = Minio(
            endpoint,
            access_key=config.MINIO_ACCESS_KEY,
            secret_key=config.MINIO_SECRET_KEY,
            secure=config.MINIO_SECURE,
        )
        logger.info("MinIO 客户端已初始化: %s (bucket=%s)", endpoint, config.MINIO_BUCKET)
    return _client


def ensure_bucket() -> None:
    """确保目标桶存在，不存在则自动创建。"""
    client = get_client()
    if not client.bucket_exists(config.MINIO_BUCKET):
        client.make_bucket(config.MINIO_BUCKET)
        logger.info("已创建 MinIO 桶: %s", config.MINIO_BUCKET)


def upload_bytes(data: bytes, object_name: str, content_type: str = "image/jpeg") -> str:
    """将字节数据上传到 MinIO，返回对象的 HTTP 访问 URL。"""
    client = get_client()
    ensure_bucket()

    client.put_object(
        config.MINIO_BUCKET,
        object_name,
        BytesIO(data),
        length=len(data),
        content_type=content_type,
    )

    scheme = "https" if config.MINIO_SECURE else "http"
    url = f"{scheme}://{config.MINIO_ENDPOINT}/{config.MINIO_BUCKET}/{object_name}"
    logger.debug("已上传到 MinIO: %s", url)
    return url
