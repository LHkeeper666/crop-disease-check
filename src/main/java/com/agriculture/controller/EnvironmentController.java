package com.agriculture.controller;

import com.agriculture.dto.EnvironmentReportDTO;
import com.agriculture.entity.EnvironmentRecord;
import com.agriculture.service.EnvironmentService;
import com.agriculture.vo.EnvironmentCurrentVO;
import com.agriculture.vo.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/environment")
public class EnvironmentController {

    @Resource
    private EnvironmentService environmentService;

    @GetMapping("/current")
    public Result<EnvironmentCurrentVO> current(
            @RequestParam(required = false) String greenhouseId) {
        return Result.success(environmentService.getCurrentData(greenhouseId));
    }

    @GetMapping("/history")
    public Result<IPage<EnvironmentRecord>> history(
            @RequestParam(required = false) String greenhouseId,
            @RequestParam(required = false) String metrics,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        return Result.success(environmentService.getHistoryData(greenhouseId, metrics, startDate, endDate, page, size));
    }

    @PostMapping("/report")
    public Result<String> report(@Valid @RequestBody EnvironmentReportDTO dto) {
        String id = environmentService.reportData(dto);
        return Result.success("环境数据上报成功", id);
    }
}
