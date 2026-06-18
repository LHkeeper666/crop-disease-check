package com.agriculture.modules.annotation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class AnnotationSaveDTO {
    @NotNull(message = "工单ID不能为空")
    private Long workOrderId;

    @NotBlank(message = "图片URL不能为空")
    private String imageUrl;

    @NotBlank(message = "标注类型不能为空")
    private String pipeline;

    @NotEmpty(message = "标注框列表不能为空")
    private List<AnnotationBoxDTO> boxes;
}
