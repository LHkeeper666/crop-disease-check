package com.agriculture.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 防治方案版本历史VO
 */
@Data
public class PreventionPlanVersionVO {

    private Long id;
    private String content;
    private LocalDate suggestTime;
    private Integer version;
    private LocalDateTime createdAt;
}
