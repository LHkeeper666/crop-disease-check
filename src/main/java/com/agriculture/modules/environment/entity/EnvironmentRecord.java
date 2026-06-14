package com.agriculture.modules.environment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("environment_record")
public class EnvironmentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("greenhouse_id")
    private String greenhouseId;

    @TableField("company_id")
    private String companyId;

    @TableField("air_temp")
    private BigDecimal airTemp;

    @TableField("soil_moisture")
    private BigDecimal soilMoisture;

    @TableField("humidity")
    private BigDecimal humidity;

    @TableField("light_level")
    private BigDecimal lightLevel;

    @TableField("co2")
    private BigDecimal co2;

    @TableField("soil_ph")
    private BigDecimal soilPh;

    @TableField("ec")
    private BigDecimal ec;

    @TableField("nitrogen")
    private BigDecimal nitrogen;

    @TableField("phosphorus")
    private BigDecimal phosphorus;

    @TableField("potassium")
    private BigDecimal potassium;

    @TableField("energy_current")
    private BigDecimal energyCurrent;

    @TableField("energy_max")
    private BigDecimal energyMax;

    @TableField("recorded_at")
    private LocalDateTime recordedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("deleted")
    private Byte deleted;
}
