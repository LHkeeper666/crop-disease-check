package com.agriculture.controller;

import com.agriculture.dto.DailyReportGenerateDTO;
import com.agriculture.service.DailyReportService;
import com.agriculture.vo.DailyReportDetailVO;
import com.agriculture.vo.DailyReportVO;
import com.agriculture.vo.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/daily-report")
public class DailyReportController {

    @Resource
    private DailyReportService dailyReportService;

    @GetMapping("/list")
    public Result<IPage<DailyReportVO>> listReports(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(dailyReportService.listReports(startDate, endDate, page, size));
    }

    @GetMapping("/{id}")
    public Result<DailyReportDetailVO> getReportDetail(@PathVariable String id) {
        return Result.success(dailyReportService.getReportDetail(id));
    }

    @PostMapping("/generate")
    public Result<String> generateReport(@Valid @RequestBody DailyReportGenerateDTO dto) {
        String reportId = dailyReportService.generateReport(dto);
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
            throw new com.agriculture.exception.BusinessException(500, "PDF生成失败: " + e.getMessage());
        }
    }
}
