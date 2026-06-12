package com.agriculture.modules.report.service.impl;

import cn.hutool.core.util.IdUtil;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.common.service.FileStorageService;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.modules.grid.mapper.GridMapper;
import com.agriculture.modules.report.dto.ReportQueryDTO;
import com.agriculture.modules.report.dto.ReportUploadDTO;
import com.agriculture.modules.report.entity.Report;
import com.agriculture.modules.report.mapper.ReportMapper;
import com.agriculture.modules.report.service.ReportService;
import com.agriculture.modules.report.vo.ReportDetailVO;
import com.agriculture.modules.report.vo.ReportListVO;
import com.agriculture.modules.report.vo.ReportUploadVO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 图像上报记录表 服务实现类
 */
@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final GridMapper gridMapper;
    private final SysUserMapper sysUserMapper;
    private final FileStorageService fileStorageService;

    @Value("${file.allowed-types:image/jpeg,image/png,image/jpg}")
    private String allowedTypes;

    private static final int MAX_FILE_COUNT = 10;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public ReportServiceImpl(GridMapper gridMapper, SysUserMapper sysUserMapper, FileStorageService fileStorageService) {
        this.gridMapper = gridMapper;
        this.sysUserMapper = sysUserMapper;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    public ReportUploadVO uploadImages(MultipartFile[] files, ReportUploadDTO dto, String userId) {
        // 校验文件数量
        if (files == null || files.length == 0) {
            throw new BusinessException(40040, "请上传至少一张图片");
        }
        if (files.length > MAX_FILE_COUNT) {
            throw new BusinessException(40042, "单次上传图片数量超过10张");
        }

        // 校验并上传文件到MinIO
        List<String> imageUrls = new ArrayList<>();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (MultipartFile file : files) {
            // 校验文件格式
            String contentType = file.getContentType();
            if (contentType == null || !Arrays.asList(allowedTypes.split(",")).contains(contentType)) {
                throw new BusinessException(40040, "图片格式不支持（仅支持JPG/PNG）");
            }

            // 校验文件大小
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(40041, "图片大小超过10MB限制");
            }

            // 生成对象名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = "images/report/" + dateStr + "-" + UUID.randomUUID().toString().replace("-", "") + extension;

            // 上传到MinIO
            String url = fileStorageService.upload(file, objectName);
            imageUrls.add(url);
        }

        // 创建上报记录
        Report report = new Report();
        report.setId(IdUtil.fastSimpleUUID());
        report.setUserId(userId);
        report.setGridId(dto.getGridId());
        report.setCropType(dto.getCropType());
        report.setImageUrls(String.join(",", imageUrls));
        report.setFoundAt(dto.getFoundAt());
        report.setDescription(dto.getDescription());
        report.setStatus("PENDING_RECOGNITION");
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        report.setDeleted((byte) 0);

        save(report);

        log.info("图像上报成功: reportId={}, userId={}, imageCount={}", report.getId(), userId, imageUrls.size());

        return ReportUploadVO.builder()
                .reportId(report.getId())
                .imageUrls(imageUrls)
                .status(report.getStatus())
                .build();
    }

    @Override
    public Page<ReportListVO> getMyReports(ReportQueryDTO dto, String userId) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getUserId, userId);

        // 状态筛选
        if (StringUtils.hasText(dto.getStatus())) {
            wrapper.eq(Report::getStatus, dto.getStatus());
        }

        // 日期筛选
        if (dto.getStartDate() != null) {
            wrapper.ge(Report::getCreatedAt, dto.getStartDate().atStartOfDay());
        }
        if (dto.getEndDate() != null) {
            wrapper.le(Report::getCreatedAt, dto.getEndDate().atTime(23, 59, 59));
        }

        wrapper.orderByDesc(Report::getCreatedAt);

        Page<Report> page = page(new Page<>(dto.getPage(), dto.getSize()), wrapper);

        // 转换为 VO
        Page<ReportListVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<ReportListVO> records = page.getRecords().stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList());
        voPage.setRecords(records);

        return voPage;
    }

    @Override
    public ReportDetailVO getReportDetail(String id) {
        Report report = getById(id);
        if (report == null) {
            throw new BusinessException("上报记录不存在");
        }

        // 获取上报人姓名
        String reporterName = null;
        if (StringUtils.hasText(report.getUserId())) {
            SysUser user = sysUserMapper.selectById(report.getUserId());
            if (user != null) {
                reporterName = user.getName();
            }
        }

        // 获取网格标签
        String gridLabel = null;
        if (StringUtils.hasText(report.getGridId())) {
            Grid grid = gridMapper.selectById(report.getGridId());
            if (grid != null) {
                gridLabel = grid.getLabel();
            }
        }

        return ReportDetailVO.builder()
                .id(report.getId())
                .reporterName(reporterName)
                .gridLabel(gridLabel)
                .cropType(report.getCropType())
                .imageUrls(parseImageUrls(report.getImageUrls()))
                .foundAt(report.getFoundAt())
                .description(report.getDescription())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                // 以下字段由其他模块（识别、审核、防治方案）填充，暂返回null
                .recognitionResult(null)
                .auditInfo(null)
                .preventionPlan(null)
                .rejectReason(null)
                .build();
    }

    /**
     * Report -> ReportListVO 转换
     */
    private ReportListVO convertToListVO(Report report) {
        // 获取网格标签
        String gridLabel = null;
        if (StringUtils.hasText(report.getGridId())) {
            Grid grid = gridMapper.selectById(report.getGridId());
            if (grid != null) {
                gridLabel = grid.getLabel();
            }
        }

        return ReportListVO.builder()
                .id(report.getId())
                .gridLabel(gridLabel)
                .cropType(report.getCropType())
                .imageUrls(parseImageUrls(report.getImageUrls()))
                .foundAt(report.getFoundAt())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                // recognitionResult 和 auditInfo 由其他模块填充，暂返回null
                .recognitionResult(null)
                .auditInfo(null)
                .build();
    }

    /**
     * 解析图片URL字符串为列表
     */
    private List<String> parseImageUrls(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return new ArrayList<>();
        }
        return Arrays.asList(imageUrls.split(","));
    }
}
