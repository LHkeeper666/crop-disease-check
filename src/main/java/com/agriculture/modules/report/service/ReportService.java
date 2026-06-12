package com.agriculture.modules.report.service;

import com.agriculture.modules.report.dto.ReportQueryDTO;
import com.agriculture.modules.report.dto.ReportUploadDTO;
import com.agriculture.modules.report.entity.Report;
import com.agriculture.modules.report.vo.ReportDetailVO;
import com.agriculture.modules.report.vo.ReportListVO;
import com.agriculture.modules.report.vo.ReportUploadVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图像上报记录表 服务类
 */
public interface ReportService extends IService<Report> {

    /**
     * 上传图片并上报
     *
     * @param files  图片文件数组
     * @param dto    上报参数
     * @param userId 当前用户ID
     * @return 上报结果
     */
    ReportUploadVO uploadImages(MultipartFile[] files, ReportUploadDTO dto, String userId);

    /**
     * 我的上报记录（分页）
     *
     * @param dto    查询参数
     * @param userId 当前用户ID
     * @return 分页结果
     */
    Page<ReportListVO> getMyReports(ReportQueryDTO dto, String userId);

    /**
     * 上报详情
     *
     * @param id 上报记录ID
     * @return 详情
     */
    ReportDetailVO getReportDetail(String id);
}
