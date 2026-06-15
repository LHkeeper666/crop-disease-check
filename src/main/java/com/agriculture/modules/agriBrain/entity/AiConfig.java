package com.agriculture.modules.agriBrain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("ai_config")
public class AiConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("config_key")
    private String configKey;

    @TableField("config_value")
    private String configValue;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
