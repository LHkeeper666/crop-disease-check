package com.agriculture.modules.camera.service.impl;

import com.agriculture.modules.camera.entity.Camera;
import com.agriculture.modules.camera.mapper.CameraMapper;
import com.agriculture.modules.camera.service.CameraService;
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
