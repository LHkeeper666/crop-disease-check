"""MinIO 对象存储客户端，负责桶初始化与文件上传。"""

from __future__ import annotations

import json
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
    """确保目标桶存在，不存在则自动创建，并设置公开读取策略。"""
    client = get_client()
    if not client.bucket_exists(config.MINIO_BUCKET):
        client.make_bucket(config.MINIO_BUCKET)
        logger.info("已创建 MinIO 桶: %s", config.MINIO_BUCKET)

    # 设置公开读取策略，允许浏览器通过 URL 直接访问对象
    policy = {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": {"AWS": ["*"]},
                "Action": ["s3:GetObject"],
                "Resource": [f"arn:aws:s3:::{config.MINIO_BUCKET}/*"],
            }
        ],
    }
    try:
        client.set_bucket_policy(config.MINIO_BUCKET, json.dumps(policy))
        logger.info("已设置桶 %s 公开读取策略", config.MINIO_BUCKET)
    except S3Error as exc:
        logger.warning("设置桶策略失败: %s", exc)


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

    # 使用公开端点构造 URL（浏览器可访问），而非容器内部地址
    public_ep = config.MINIO_PUBLIC_ENDPOINT
    if "://" not in public_ep:
        public_ep = f"{'https' if config.MINIO_SECURE else 'http'}://{public_ep}"
    url = f"{public_ep}/{config.MINIO_BUCKET}/{object_name}"
    logger.debug("已上传到 MinIO: %s", url)
    return url
