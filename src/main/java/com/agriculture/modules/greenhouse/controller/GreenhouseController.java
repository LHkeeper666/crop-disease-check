package com.agriculture.modules.greenhouse.controller;

import com.agriculture.modules.greenhouse.dto.GreenhouseDTO;
import com.agriculture.modules.greenhouse.entity.Greenhouse;
import com.agriculture.modules.greenhouse.service.GreenhouseService;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.common.vo.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
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

    @Resource
    private SysUserMapper sysUserMapper;

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
    public Result<String> create(HttpServletRequest request, @Valid @RequestBody GreenhouseDTO dto) {
        String userId = (String) request.getAttribute("userId");
        String companyId = resolveCompanyId(userId);
        String id = greenhouseService.createGreenhouse(dto, companyId);
        return Result.success("温室创建成功", id);
    }

    /**
     * 根据用户ID解析企业ID
     */
    private String resolveCompanyId(String userId) {
        if (userId == null) return "";
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getCompanyId() != null && !user.getCompanyId().isEmpty()) {
            return user.getCompanyId();
        }
        return "";
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
