package com.agriculture.modules.annotation.service;

import com.agriculture.modules.annotation.dto.AnnotationSaveDTO;
import com.agriculture.modules.annotation.entity.Annotation;
import com.agriculture.modules.annotation.vo.AnnotationVO;
import com.agriculture.modules.annotation.vo.ClassOptionVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AnnotationService extends IService<Annotation> {

    /** 保存（创建或更新）标注及所有标注框 */
    Long saveAnnotation(AnnotationSaveDTO dto, String userId, String companyId);

    /** 获取工单的标注详情（最新一条） */
    AnnotationVO getAnnotationByWorkOrderId(Long workOrderId);

    /** 获取类别列表（自动补全用） */
    List<ClassOptionVO> getClassOptions(String pipeline);

    /** 导出 YOLO 格式 txt */
    String exportYoloTxt(Long annotationId);
}
