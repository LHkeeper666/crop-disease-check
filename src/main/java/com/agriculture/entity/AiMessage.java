package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 农业大脑消息表
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("ai_message")
public class AiMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息UUID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private String id;

    /**
     * 对话ID
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 角色: USER/ASSISTANT
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
