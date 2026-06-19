package com.agriculture.modules.annotation.service.impl;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.annotation.dto.AnnotationBoxDTO;
import com.agriculture.modules.annotation.dto.AnnotationSaveDTO;
import com.agriculture.modules.annotation.entity.Annotation;
import com.agriculture.modules.annotation.entity.AnnotationBox;
import com.agriculture.modules.annotation.mapper.AnnotationBoxMapper;
import com.agriculture.modules.annotation.mapper.AnnotationMapper;
import com.agriculture.modules.annotation.service.AnnotationService;
import com.agriculture.modules.annotation.vo.AnnotationBoxVO;
import com.agriculture.modules.annotation.vo.AnnotationVO;
import com.agriculture.modules.annotation.vo.ClassOptionVO;
import com.agriculture.modules.pestDiseaseInfo.entity.DiseaseInfo;
import com.agriculture.modules.pestDiseaseInfo.entity.PestInfo;
import com.agriculture.modules.pestDiseaseInfo.mapper.DiseaseInfoMapper;
import com.agriculture.modules.pestDiseaseInfo.mapper.PestInfoMapper;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnnotationServiceImpl extends ServiceImpl<AnnotationMapper, Annotation> implements AnnotationService {

    @Resource
    private AnnotationBoxMapper annotationBoxMapper;

    @Resource
    private DiseaseInfoMapper diseaseInfoMapper;

    @Resource
    private PestInfoMapper pestInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAnnotation(AnnotationSaveDTO dto, String userId, String companyId) {
        // 查找已有标注记录
        Annotation existing = getOne(new LambdaQueryWrapper<Annotation>()
                .eq(Annotation::getWorkOrderId, dto.getWorkOrderId())
                .eq(Annotation::getDeleted, (byte) 0)
                .last("LIMIT 1"));

        Long annotationId;
        if (existing != null) {
            // 更新：删除旧 boxes，更新 annotation
            annotationId = existing.getId();
            annotationBoxMapper.delete(new LambdaQueryWrapper<AnnotationBox>()
                    .eq(AnnotationBox::getAnnotationId, annotationId));
            existing.setImageUrl(dto.getImageUrl());
            existing.setPipeline(dto.getPipeline());
            existing.setUpdatedAt(LocalDateTime.now());
            updateById(existing);
        } else {
            // 新建
            Annotation annotation = new Annotation();
            annotation.setWorkOrderId(dto.getWorkOrderId());
            annotation.setImageUrl(dto.getImageUrl());
            annotation.setPipeline(dto.getPipeline());
            annotation.setCreatedBy(userId);
            annotation.setCompanyId(companyId);
            annotation.setCreatedAt(LocalDateTime.now());
            annotation.setUpdatedAt(LocalDateTime.now());
            annotation.setDeleted((byte) 0);
            save(annotation);
            annotationId = annotation.getId();
        }

        // 批量插入标注框
        for (AnnotationBoxDTO boxDto : dto.getBoxes()) {
            AnnotationBox box = new AnnotationBox();
            box.setAnnotationId(annotationId);
            box.setClassId(boxDto.getClassId());
            box.setClassName(boxDto.getClassName());
            box.setNameCn(boxDto.getNameCn());
            box.setX(boxDto.getX());
            box.setY(boxDto.getY());
            box.setWidth(boxDto.getWidth());
            box.setHeight(boxDto.getHeight());
            box.setCreatedAt(LocalDateTime.now());
            annotationBoxMapper.insert(box);
        }

        return annotationId;
    }

    @Override
    public AnnotationVO getAnnotationByWorkOrderId(Long workOrderId) {
        Annotation annotation = getOne(new LambdaQueryWrapper<Annotation>()
                .eq(Annotation::getWorkOrderId, workOrderId)
                .eq(Annotation::getDeleted, (byte) 0)
                .orderByDesc(Annotation::getCreatedAt)
                .last("LIMIT 1"));

        if (annotation == null) {
            return null;
        }

        return toVO(annotation);
    }

    @Override
    public List<ClassOptionVO> getClassOptions(String pipeline) {
        if ("pest".equalsIgnoreCase(pipeline)) {
            List<PestInfo> pests = pestInfoMapper.selectList(new LambdaQueryWrapper<PestInfo>()
                    .eq(PestInfo::getDeleted, (byte) 0)
                    .orderByAsc(PestInfo::getId));
            return pests.stream().map(p -> {
                ClassOptionVO vo = new ClassOptionVO();
                vo.setId(p.getId());
                vo.setClassName(p.getPestName());
                vo.setNameCn(p.getPestName());
                return vo;
            }).collect(Collectors.toList());
        } else {
            // 默认 disease
            List<DiseaseInfo> diseases = diseaseInfoMapper.selectList(new LambdaQueryWrapper<DiseaseInfo>()
                    .eq(DiseaseInfo::getDeleted, (byte) 0)
                    .orderByAsc(DiseaseInfo::getId));
            return diseases.stream().map(d -> {
                ClassOptionVO vo = new ClassOptionVO();
                vo.setId(d.getId());
                vo.setClassName(d.getDiseaseName());
                vo.setNameCn(d.getNameCn());
                return vo;
            }).collect(Collectors.toList());
        }
    }

    @Override
    public String exportYoloTxt(Long annotationId) {
        List<AnnotationBox> boxes = annotationBoxMapper.selectList(
                new LambdaQueryWrapper<AnnotationBox>()
                        .eq(AnnotationBox::getAnnotationId, annotationId)
                        .orderByAsc(AnnotationBox::getId));

        StringBuilder sb = new StringBuilder();
        for (AnnotationBox box : boxes) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(box.getClassId())
              .append(' ').append(String.format("%.6f", box.getX()))
              .append(' ').append(String.format("%.6f", box.getY()))
              .append(' ').append(String.format("%.6f", box.getWidth()))
              .append(' ').append(String.format("%.6f", box.getHeight()));
        }
        return sb.toString();
    }

    private AnnotationVO toVO(Annotation annotation) {
        AnnotationVO vo = new AnnotationVO();
        vo.setId(annotation.getId());
        vo.setWorkOrderId(annotation.getWorkOrderId());
        vo.setImageUrl(annotation.getImageUrl());
        vo.setPipeline(annotation.getPipeline());
        vo.setCreatedBy(annotation.getCreatedBy());
        vo.setCreatedAt(annotation.getCreatedAt());

        // 解析标注人姓名
        if (annotation.getCreatedBy() != null) {
            SysUser user = sysUserMapper.selectById(annotation.getCreatedBy());
            if (user != null) {
                vo.setCreatedByName(user.getName());
            }
        }

        // 查询标注框
        List<AnnotationBox> boxes = annotationBoxMapper.selectList(
                new LambdaQueryWrapper<AnnotationBox>()
                        .eq(AnnotationBox::getAnnotationId, annotation.getId())
                        .orderByAsc(AnnotationBox::getId));

        List<AnnotationBoxVO> boxVOs = boxes.stream().map(box -> {
            AnnotationBoxVO bvo = new AnnotationBoxVO();
            bvo.setId(box.getId());
            bvo.setClassId(box.getClassId());
            bvo.setClassName(box.getClassName());
            bvo.setNameCn(box.getNameCn());
            bvo.setX(box.getX());
            bvo.setY(box.getY());
            bvo.setWidth(box.getWidth());
            bvo.setHeight(box.getHeight());
            return bvo;
        }).collect(Collectors.toList());

        vo.setBoxes(boxVOs);
        return vo;
    }
}
