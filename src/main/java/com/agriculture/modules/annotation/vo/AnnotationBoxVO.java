package com.agriculture.modules.annotation.vo;

import lombok.Data;

@Data
public class AnnotationBoxVO {
    private Long id;
    private Integer classId;
    private String className;
    private String nameCn;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
}
