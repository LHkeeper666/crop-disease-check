package com.agriculture.service.impl;

import com.agriculture.entity.Report;
import com.agriculture.dao.mapper.ReportMapper;
import com.agriculture.service.ReportService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 图像上报记录表 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

}
