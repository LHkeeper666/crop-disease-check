package com.agriculture.modules.dailyReport.controller;

import com.agriculture.modules.dailyReport.dto.DailyReportGenerateDTO;
import com.agriculture.modules.dailyReport.service.DailyReportService;
import com.agriculture.modules.dailyReport.vo.DailyReportDetailVO;
import com.agriculture.modules.dailyReport.vo.DailyReportVO;
import com.agriculture.common.vo.Result;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.agriculture.modules.user.entity.SysUser;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/daily-report")
public class DailyReportController {

    @Resource
    private DailyReportService dailyReportService;

    @Resource
    private SysUserMapper sysUserMapper;

    private String resolveCompanyId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) return "";
        SysUser user = sysUserMapper.selectById(userId);
        return (user != null && StringUtils.hasText(user.getCompanyId())) ? user.getCompanyId() : "";
    }

    @GetMapping("/list")
    public Result<IPage<DailyReportVO>> listReports(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return Result.success(dailyReportService.listReports(startDate, endDate, page, size, resolveCompanyId(request)));
    }

    @GetMapping("/{id}")
    public Result<DailyReportDetailVO> getReportDetail(@PathVariable String id) {
        return Result.success(dailyReportService.getReportDetail(id));
    }

    @PostMapping("/generate")
    public Result<String> generateReport(@Valid @RequestBody DailyReportGenerateDTO dto,
                                          HttpServletRequest request) {
        String reportId = dailyReportService.generateReport(dto, resolveCompanyId(request));
        return Result.success("日报生成成功", reportId);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String id) {
        DailyReportDetailVO detail = dailyReportService.getReportDetail(id);
        String htmlContent = detail.getHtmlContent();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            ByteArrayInputStream htmlStream = new ByteArrayInputStream(htmlContent.getBytes("UTF-8"));
            XMLWorkerHelper.getInstance().parseXHtml(writer, document, htmlStream);

            document.close();

            byte[] pdfBytes = baos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "daily-report-" + detail.getReportDate() + ".pdf");
            headers.setContentLength(pdfBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (DocumentException | IOException e) {
            throw new com.agriculture.common.exception.BusinessException(500, "PDF生成失败: " + e.getMessage());
        }
    }
}
