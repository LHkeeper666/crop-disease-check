package com.agriculture.modules.agriBrain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VisibleData {

    private List<Map<String, Object>> list;
    private Map<String, Object> stats;
    private Map<String, String> filters;
    private Map<String, Object> extra;
}
