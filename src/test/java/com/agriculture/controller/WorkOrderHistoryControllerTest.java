package com.agriculture.controller;

import com.agriculture.modules.workorder.controller.WorkOrderHistoryController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 工单状态历史 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderHistoryController 工单状态历史控制器测试")
class WorkOrderHistoryControllerTest {

    @InjectMocks
    private WorkOrderHistoryController workOrderHistoryController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(workOrderHistoryController);
    }
}
