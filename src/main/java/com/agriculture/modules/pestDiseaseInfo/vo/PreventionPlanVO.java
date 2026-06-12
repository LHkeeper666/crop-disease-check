package com.agriculture.modules.pestDiseaseInfo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 防治方案VO
 */
@Data
public class PreventionPlanVO {

    private String id;
    private String content;
    private LocalDate suggestTime;
    private String authorName;
    private Integer version;
    private LocalDateTime createdAt;

    /** 历史版本列表 */
    private List<PreventionPlanVersionVO> versions;
}
