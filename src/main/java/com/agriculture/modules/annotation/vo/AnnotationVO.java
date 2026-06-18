package com.agriculture.modules.annotation.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnnotationVO {
    private Long id;
    private Long workOrderId;
    private String imageUrl;
    private String pipeline;
    private String createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private List<AnnotationBoxVO> boxes;
}
