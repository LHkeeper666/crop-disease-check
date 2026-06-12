package com.agriculture.common.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 */
public interface FileStorageService {

    /**
     * 上传文件到MinIO
     *
     * @param file       文件
     * @param objectName 对象名（如 images/report/20260612-uuid.jpg）
     * @return 文件访问URL
     */
    String upload(MultipartFile file, String objectName);
}
