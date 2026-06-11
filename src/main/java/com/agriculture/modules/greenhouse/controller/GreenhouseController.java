package com.agriculture.modules.greenhouse.controller;

import com.agriculture.modules.greenhouse.dto.GreenhouseDTO;
import com.agriculture.modules.greenhouse.entity.Greenhouse;
import com.agriculture.modules.greenhouse.service.GreenhouseService;
import com.agriculture.common.vo.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 温室/大棚表 前端控制器
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-10
 */
@RestController
@RequestMapping("/greenhouse")
public class GreenhouseController {

    @Resource
    private GreenhouseService greenhouseService;

    /**
     * 温室列表
     */
    @GetMapping("/list")
    public Result<IPage<Greenhouse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(greenhouseService.listGreenhouses(status, keyword, page, size));
    }

    /**
     * 温室详情
     */
    @GetMapping("/{id}")
    public Result<Greenhouse> detail(@PathVariable String id) {
        return Result.success(greenhouseService.getGreenhouseDetail(id));
    }

    /**
     * 新增温室
     */
    @PostMapping
    public Result<String> create(@Valid @RequestBody GreenhouseDTO dto) {
        // TODO: 从 SecurityContext 获取 companyId，暂时使用默认值
        String companyId = "default-company";
        String id = greenhouseService.createGreenhouse(dto, companyId);
        return Result.success("温室创建成功", id);
    }

    /**
     * 修改温室
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody GreenhouseDTO dto) {
        greenhouseService.updateGreenhouse(id, dto);
        return Result.success("温室更新成功", null);
    }

    /**
     * 删除温室
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        greenhouseService.deleteGreenhouse(id);
        return Result.success("温室删除成功", null);
    }
}
