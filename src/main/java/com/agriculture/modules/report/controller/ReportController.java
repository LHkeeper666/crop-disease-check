package com.agriculture.modules.report.controller;

import com.agriculture.common.vo.Result;
import com.agriculture.modules.report.dto.ReportQueryDTO;
import com.agriculture.modules.report.dto.ReportUploadDTO;
import com.agriculture.modules.report.service.ReportService;
import com.agriculture.modules.report.vo.ReportDetailVO;
import com.agriculture.modules.report.vo.ReportListVO;
import com.agriculture.modules.report.vo.ReportUploadVO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图像上报控制器
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final SysUserMapper sysUserMapper;

    /**
     * 9.1 上传图片并上报
     */
    @PostMapping("/upload")
    public Result<ReportUploadVO> upload(
            @RequestParam("images") MultipartFile[] images,
            @Valid ReportUploadDTO dto,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        String companyId = resolveCompanyId(userId);
        ReportUploadVO vo = reportService.uploadImages(images, dto, userId, companyId);
        return Result.success("上报成功，正在进行识别", vo);
    }

    /**
     * 9.2 我的上报记录
     */
    @GetMapping("/mine")
    public Result<Page<ReportListVO>> mine(
            ReportQueryDTO dto,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        String companyId = resolveCompanyId(userId);
        Page<ReportListVO> page = reportService.getMyReports(dto, userId, companyId);
        return Result.success(page);
    }

    /**
     * 9.3 上报详情
     */
    @GetMapping("/{id}")
    public Result<ReportDetailVO> detail(@PathVariable String id) {
        ReportDetailVO vo = reportService.getReportDetail(id);
        return Result.success(vo);
    }

    private String resolveCompanyId(String userId) {
        if (userId == null) return "";
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && StringUtils.hasText(user.getCompanyId())) {
            return user.getCompanyId();
        }
        return "";
    }
}
