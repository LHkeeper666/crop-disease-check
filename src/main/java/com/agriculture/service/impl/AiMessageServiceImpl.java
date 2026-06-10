package com.agriculture.service.impl;

import com.agriculture.entity.AiMessage;
import com.agriculture.dao.mapper.AiMessageMapper;
import com.agriculture.service.AiMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 农业大脑消息表 服务实现类
 * </p>
 *
 * @author agriculture-team
 * @since 2026-06-09
 */
@Service
public class AiMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements AiMessageService {

}
