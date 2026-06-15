package com.agriculture.modules.workorder.vo;

import lombok.Data;

@Data
public class EmailPreviewVO {

    private String toUserId;
    private String toName;
    private String toEmail;
    private String subject;
    private String content;
}
