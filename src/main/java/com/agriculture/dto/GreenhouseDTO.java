package com.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 温室新增/修改请求体
 */
@Data
public class GreenhouseDTO {

    /**
     * 区域编号(如GH-A1)
     */
    @NotBlank(message = "区域编号不能为空")
    @Size(max = 32, message = "区域编号长度不能超过32")
    private String sectorId;

    /**
     * 作物种类
     */
    private String cropSpecies;

    /**
     * 定植日期
     */
    private LocalDate plantingDate;

    /**
     * 地理位置坐标
     */
    private String location;

    /**
     * 面积(m²)
     */
    private BigDecimal area;

    /**
     * 状态: ACTIVE/INACTIVE/MAINTENANCE
     */
    private String status;
}
