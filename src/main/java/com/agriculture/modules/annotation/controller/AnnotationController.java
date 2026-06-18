package com.agriculture.modules.annotation.controller;

import com.agriculture.common.annotation.RequireRole;
import com.agriculture.common.vo.Result;
import com.agriculture.modules.annotation.dto.AnnotationSaveDTO;
import com.agriculture.modules.annotation.service.AnnotationService;
import com.agriculture.modules.annotation.vo.AnnotationVO;
import com.agriculture.modules.annotation.vo.ClassOptionVO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/annotation")
public class AnnotationController {

    @Resource
    private AnnotationService annotationService;

    @Resource
    private SysUserMapper sysUserMapper;

    /** 保存/更新标注 */
    @PostMapping("/save")
    @RequireRole({"EXPERT", "ADMIN"})
    public Result<Long> saveAnnotation(@Valid @RequestBody AnnotationSaveDTO dto,
                                        HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        String companyId = resolveCompanyId(userId);
        Long id = annotationService.saveAnnotation(dto, userId, companyId);
        return Result.success("标注保存成功", id);
    }

    /** 获取工单的标注详情 */
    @GetMapping("/work-order/{workOrderId}")
    public Result<AnnotationVO> getAnnotation(@PathVariable Long workOrderId) {
        AnnotationVO vo = annotationService.getAnnotationByWorkOrderId(workOrderId);
        return Result.success(vo);
    }

    /** 获取类别列表（自动补全用） */
    @GetMapping("/classes")
    public Result<List<ClassOptionVO>> getClassOptions(@RequestParam String pipeline) {
        return Result.success(annotationService.getClassOptions(pipeline));
    }

    /** 导出 YOLO 格式 txt */
    @GetMapping("/{id}/export-yolo")
    @RequireRole({"EXPERT", "ADMIN"})
    public Result<String> exportYolo(@PathVariable Long id) {
        return Result.success(annotationService.exportYoloTxt(id));
    }

    private String resolveCompanyId(String userId) {
        if (userId == null) return "";
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getCompanyId() != null && !user.getCompanyId().isEmpty()) {
            return user.getCompanyId();
        }
        return "";
    }
}
