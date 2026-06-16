package com.agriculture.modules.agriBrain.dto;

import lombok.Data;

@Data
public class PageContext {

    private String page;
    private String pageName;
    private String selectedId;
    private VisibleData visibleData;
}
