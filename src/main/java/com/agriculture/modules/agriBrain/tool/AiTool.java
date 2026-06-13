package com.agriculture.modules.agriBrain.tool;

import java.util.Map;

public interface AiTool {

    String getName();

    String getDescription();

    Map<String, Object> getParameters();

    String execute(Map<String, Object> arguments, String userId, String companyId);
}
