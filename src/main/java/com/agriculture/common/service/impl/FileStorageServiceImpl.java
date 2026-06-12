package com.agriculture.common.service.impl;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.service.FileStorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * MinIO 文件存储服务实现
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket-name}")
    private String bucketName;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        log.info("MinIO 客户端初始化完成: endpoint={}, bucket={}", endpoint, bucketName);
    }

    @Override
    public String upload(MultipartFile file, String objectName) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // 返回可访问的URL
            String url = endpoint + "/" + bucketName + "/" + objectName;
            log.info("文件上传MinIO成功: {}", url);
            return url;
        } catch (Exception e) {
            log.error("文件上传MinIO失败: {}", e.getMessage(), e);
            throw new BusinessException("文件上传失败");
        }
    }
}
