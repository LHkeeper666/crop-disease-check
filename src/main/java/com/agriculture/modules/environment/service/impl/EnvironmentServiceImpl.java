package com.agriculture.modules.environment.service.impl;

import com.agriculture.modules.environment.mapper.EnvironmentMapper;
import com.agriculture.modules.greenhouse.mapper.GreenhouseMapper;
import com.agriculture.modules.environment.dto.EnvironmentReportDTO;
import com.agriculture.modules.environment.entity.EnvironmentRecord;
import com.agriculture.modules.greenhouse.entity.Greenhouse;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.environment.service.EnvironmentService;
import com.agriculture.modules.environment.vo.EnvironmentCurrentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EnvironmentServiceImpl extends ServiceImpl<EnvironmentMapper, EnvironmentRecord> implements EnvironmentService {

    private static final BigDecimal WARNING_RATIO = new BigDecimal("0.1");

    // 阈值配置：key = 字段名
    private static final BigDecimal TEMP_MIN = new BigDecimal("15");
    private static final BigDecimal TEMP_MAX = new BigDecimal("35");
    private static final BigDecimal SOIL_MOISTURE_MIN = new BigDecimal("40");
    private static final BigDecimal SOIL_MOISTURE_MAX = new BigDecimal("80");
    private static final BigDecimal HUMIDITY_MIN = new BigDecimal("40");
    private static final BigDecimal HUMIDITY_MAX = new BigDecimal("75");
    private static final BigDecimal LIGHT_MIN = new BigDecimal("200");
    private static final BigDecimal LIGHT_MAX = new BigDecimal("50000");

    @Resource
    private GreenhouseMapper greenhouseMapper;

    @Override
    public EnvironmentCurrentVO getCurrentData(String greenhouseId) {
        // 如果未指定 greenhouseId，取默认/首个温室
        if (!StringUtils.hasText(greenhouseId)) {
            Greenhouse first = greenhouseMapper.selectOne(
                    new LambdaQueryWrapper<Greenhouse>().last("LIMIT 1"));
            if (first == null) {
                throw new BusinessException(40100, "greenhouseId 为空或不存在");
            }
            greenhouseId = first.getId();
        }

        // 查询最新一条环境记录
        EnvironmentRecord record = getOne(new LambdaQueryWrapper<EnvironmentRecord>()
                .eq(EnvironmentRecord::getGreenhouseId, greenhouseId)
                .orderByDesc(EnvironmentRecord::getRecordedAt)
                .last("LIMIT 1"));

        if (record == null) {
            throw new BusinessException(40100, "该温室暂无环境数据");
        }

        // 查询温室信息获取 sectorId
        Greenhouse greenhouse = greenhouseMapper.selectById(greenhouseId);
        String sectorId = greenhouse != null ? greenhouse.getSectorId() : null;

        return buildCurrentVO(record, greenhouseId, sectorId);
    }

    @Override
    public IPage<EnvironmentRecord> getHistoryData(String greenhouseId, String metrics,
                                                    String startDate, String endDate,
                                                    int page, int size) {
        LambdaQueryWrapper<EnvironmentRecord> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(greenhouseId)) {
            wrapper.eq(EnvironmentRecord::getGreenhouseId, greenhouseId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(EnvironmentRecord::getRecordedAt, LocalDateTime.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(EnvironmentRecord::getRecordedAt, LocalDateTime.parse(endDate));
        }

        wrapper.orderByDesc(EnvironmentRecord::getRecordedAt);

        // 如果指定了 metrics，只查部分字段（通过 select 实现）
        if (StringUtils.hasText(metrics)) {
            Set<String> allowedMetrics = new HashSet<>(Arrays.asList(
                    "airTemp", "soilMoisture", "humidity", "lightLevel",
                    "co2", "soilPh", "ec", "nitrogen", "phosphorus",
                    "potassium", "energyCurrent", "energyMax"));
            // MyBatis-Plus select 不支持驼峰直接映射，这里简化处理：查全量字段
            // 前端自行按 metrics 过滤
        }

        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String reportData(EnvironmentReportDTO dto) {
        // 校验 greenhouseId 存在性
        Greenhouse greenhouse = greenhouseMapper.selectById(dto.getGreenhouseId());
        if (greenhouse == null) {
            throw new BusinessException(40100, "greenhouseId 为空或不存在");
        }

        // 检查上报频率（1分钟限制）
        EnvironmentRecord latest = getOne(new LambdaQueryWrapper<EnvironmentRecord>()
                .eq(EnvironmentRecord::getGreenhouseId, dto.getGreenhouseId())
                .orderByDesc(EnvironmentRecord::getRecordedAt)
                .last("LIMIT 1"));

        if (latest != null && latest.getRecordedAt() != null) {
            LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
            if (latest.getRecordedAt().isAfter(oneMinuteAgo)) {
                throw new BusinessException(40102, "数据上报频率过高，请勿超过每分钟1次");
            }
        }

        // 构建记录
        EnvironmentRecord record = new EnvironmentRecord();
        record.setGreenhouseId(dto.getGreenhouseId());
        record.setCompanyId(greenhouse.getCompanyId());
        record.setAirTemp(dto.getAirTemp());
        record.setSoilMoisture(dto.getSoilMoisture());
        record.setHumidity(dto.getHumidity());
        record.setLightLevel(dto.getLightLevel());
        record.setCo2(dto.getCo2());
        record.setSoilPh(dto.getSoilPh());
        record.setEc(dto.getEc());
        record.setNitrogen(dto.getNitrogen());
        record.setPhosphorus(dto.getPhosphorus());
        record.setPotassium(dto.getPotassium());
        record.setEnergyCurrent(dto.getEnergyCurrent());
        record.setEnergyMax(dto.getEnergyMax());
        record.setRecordedAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());

        save(record);
        return record.getId();
    }

    private EnvironmentCurrentVO buildCurrentVO(EnvironmentRecord record, String greenhouseId, String sectorId) {
        EnvironmentCurrentVO vo = new EnvironmentCurrentVO();
        vo.setGreenhouseId(greenhouseId);
        vo.setSectorId(sectorId);
        vo.setRecordedAt(record.getRecordedAt());

        // 基础环境参数（带阈值状态）
        EnvironmentCurrentVO.EnvironmentData env = new EnvironmentCurrentVO.EnvironmentData();
        env.setAirTemp(buildMetric(record.getAirTemp(), "°C", TEMP_MIN, TEMP_MAX));
        env.setSoilMoisture(buildMetric(record.getSoilMoisture(), "%", SOIL_MOISTURE_MIN, SOIL_MOISTURE_MAX));
        env.setHumidity(buildMetric(record.getHumidity(), "%", HUMIDITY_MIN, HUMIDITY_MAX));
        env.setLightLevel(buildMetric(record.getLightLevel(), "lux", LIGHT_MIN, LIGHT_MAX));
        vo.setEnvironment(env);

        // 生长指标
        EnvironmentCurrentVO.GrowthMetrics growth = new EnvironmentCurrentVO.GrowthMetrics();
        growth.setCo2(buildMetric(record.getCo2(), "ppm", null, null));
        growth.setSoilPh(buildMetric(record.getSoilPh(), "", null, null));
        growth.setEc(buildMetric(record.getEc(), "mS/cm", null, null));
        growth.setTemperature(buildMetric(record.getAirTemp(), "°C", null, null));
        growth.setNitrogen(buildMetric(record.getNitrogen(), "mg/kg", null, null));
        growth.setPhosphorus(buildMetric(record.getPhosphorus(), "mg/kg", null, null));
        growth.setPotassium(buildMetric(record.getPotassium(), "mg/kg", null, null));
        vo.setGrowthMetrics(growth);

        // 能耗数据
        EnvironmentCurrentVO.EnergyData energy = new EnvironmentCurrentVO.EnergyData();
        energy.setCurrent(record.getEnergyCurrent());
        energy.setMax(record.getEnergyMax());
        energy.setUnit("Kw");
        energy.setTrend("stable");
        vo.setEnergy(energy);

        return vo;
    }

    private EnvironmentCurrentVO.MetricValue buildMetric(BigDecimal value, String unit,
                                                          BigDecimal min, BigDecimal max) {
        if (value == null) {
            return null;
        }
        String status = calculateStatus(value, min, max);
        return new EnvironmentCurrentVO.MetricValue(value, unit, status, min, max);
    }

    private String calculateStatus(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (min == null || max == null) {
            return "normal";
        }
        if (value.compareTo(min) >= 0 && value.compareTo(max) <= 0) {
            return "normal";
        }
        // 计算超出范围的百分比
        BigDecimal threshold;
        if (value.compareTo(min) < 0) {
            threshold = min.subtract(value).divide(min, 4, RoundingMode.HALF_UP);
        } else {
            threshold = value.subtract(max).divide(max, 4, RoundingMode.HALF_UP);
        }
        if (threshold.compareTo(WARNING_RATIO) <= 0) {
            return "warning";
        }
        return "critical";
    }

    // ==================== 企业隔离查询方法 ====================

    @Override
    public List<Map<String, Object>> getLatestRecords(String companyId) {
        LambdaQueryWrapper<EnvironmentRecord> wrapper = new LambdaQueryWrapper<>();

        // 企业隔离
        if (StringUtils.hasText(companyId)) {
            wrapper.eq(EnvironmentRecord::getCompanyId, companyId);
        }

        wrapper.orderByDesc(EnvironmentRecord::getRecordedAt);

        List<EnvironmentRecord> allRecords = baseMapper.selectList(wrapper);

        // 按 greenhouseId 分组，取每组最新一条
        Map<String, EnvironmentRecord> latestByGreenhouse = allRecords.stream()
                .collect(Collectors.toMap(
                        EnvironmentRecord::getGreenhouseId,
                        r -> r,
                        (existing, replacement) -> existing.getRecordedAt() != null &&
                                replacement.getRecordedAt() != null &&
                                replacement.getRecordedAt().isAfter(existing.getRecordedAt()) ? replacement : existing,
                        LinkedHashMap::new
                ));

        return latestByGreenhouse.values().stream().map(record -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("greenhouseId", record.getGreenhouseId());
            item.put("airTemp", record.getAirTemp());
            item.put("humidity", record.getHumidity());
            item.put("soilMoisture", record.getSoilMoisture());
            item.put("co2", record.getCo2());
            item.put("lightLevel", record.getLightLevel());
            item.put("recordedAt", record.getRecordedAt() != null ?
                    record.getRecordedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getTrendData(String companyId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);

        LambdaQueryWrapper<EnvironmentRecord> wrapper = new LambdaQueryWrapper<>();

        // 企业隔离
        if (StringUtils.hasText(companyId)) {
            wrapper.eq(EnvironmentRecord::getCompanyId, companyId);
        }

        wrapper.ge(EnvironmentRecord::getRecordedAt, startDate.atStartOfDay());
        wrapper.orderByAsc(EnvironmentRecord::getRecordedAt);

        List<EnvironmentRecord> records = baseMapper.selectList(wrapper);

        // 按天分组
        Map<LocalDate, List<EnvironmentRecord>> byDay = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getRecordedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> daily = new ArrayList<>();
        for (Map.Entry<LocalDate, List<EnvironmentRecord>> entry : byDay.entrySet()) {
            List<EnvironmentRecord> dayRecords = entry.getValue();
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", entry.getKey().format(DateTimeFormatter.ofPattern("MM-dd")));
            dayData.put("count", dayRecords.size());
            dayData.put("airTemp", aggregateField(dayRecords, EnvironmentRecord::getAirTemp));
            dayData.put("humidity", aggregateField(dayRecords, EnvironmentRecord::getHumidity));
            dayData.put("soilMoisture", aggregateField(dayRecords, EnvironmentRecord::getSoilMoisture));
            dayData.put("co2", aggregateField(dayRecords, EnvironmentRecord::getCo2));
            dayData.put("lightLevel", aggregateField(dayRecords, EnvironmentRecord::getLightLevel));
            daily.add(dayData);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", startDate.format(DateTimeFormatter.ofPattern("MM-dd")) + " ~ " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd")));
        result.put("days", daily.size());
        result.put("daily", daily);

        return result;
    }

    private Map<String, Object> aggregateField(List<EnvironmentRecord> records,
                                                java.util.function.Function<EnvironmentRecord, BigDecimal> getter) {
        List<BigDecimal> values = records.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (values.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("avg", null);
            empty.put("min", null);
            empty.put("max", null);
            return empty;
        }

        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("avg", avg);
        agg.put("min", min);
        agg.put("max", max);
        return agg;
    }
}
