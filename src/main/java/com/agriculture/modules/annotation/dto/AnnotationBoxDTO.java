package com.agriculture.modules.annotation.dto;

import lombok.Data;

@Data
public class AnnotationBoxDTO {
    private Integer classId;
    private String className;
    private String nameCn;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
}
