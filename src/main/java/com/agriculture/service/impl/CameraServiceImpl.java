package com.agriculture.service.impl;

import com.agriculture.entity.Camera;
import com.agriculture.dao.mapper.CameraMapper;
import com.agriculture.service.CameraService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 摄像头表 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class CameraServiceImpl extends ServiceImpl<CameraMapper, Camera> implements CameraService {

}
