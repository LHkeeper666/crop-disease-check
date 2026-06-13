package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.agriBrain.tool.AiTool;
import com.agriculture.modules.pestDiseaseInfo.entity.DiseaseInfo;
import com.agriculture.modules.pestDiseaseInfo.entity.PestInfo;
import com.agriculture.modules.pestDiseaseInfo.mapper.DiseaseInfoMapper;
import com.agriculture.modules.pestDiseaseInfo.mapper.PestInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class PestDiseaseInfoTool implements AiTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private DiseaseInfoMapper diseaseInfoMapper;

    @Resource
    private PestInfoMapper pestInfoMapper;

    @Override
    public String getName() {
        return "pest_disease_info";
    }

    @Override
    public String getDescription() {
        return "查询病虫害知识库（38种病害 + 102种虫害）。支持两种操作：\n"
                + "- name: 按精确名称查询，返回完整的防治信息\n"
                + "- search: 按关键词模糊搜索，匹配描述、发生条件、防治方法等字段";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("name", "search"));
        action.put("description", "操作类型：name=精确名称查询，search=关键词模糊搜索");
        properties.put("action", action);

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "查询关键词（病虫害名称或症状描述）");
        properties.put("keyword", keyword);

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("enum", List.of("disease", "pest"));
        category.put("description", "查询类别：disease=仅查病害，pest=仅查虫害，不传则两者都查");
        properties.put("category", category);

        params.put("properties", properties);
        params.put("required", List.of("action", "keyword"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> arguments, String userId, String companyId) {
        String action = (String) arguments.getOrDefault("action", "name");
        String keyword = (String) arguments.get("keyword");

        if (!StringUtils.hasText(keyword)) {
            return "{\"error\": \"关键词不能为空\"}";
        }

        try {
            if ("search".equals(action)) {
                return executeSearch(keyword, (String) arguments.get("category"));
            } else {
                return executeName(keyword, (String) arguments.get("category"));
            }
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String executeName(String keyword, String category) throws JsonProcessingException {
        List<Map<String, Object>> results = new ArrayList<>();

        // 查询病害
        if (!"pest".equals(category)) {
            LambdaQueryWrapper<DiseaseInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DiseaseInfo::getNameCn, keyword)
                    .or()
                    .eq(DiseaseInfo::getDiseaseName, keyword);
            wrapper.last("LIMIT 1");

            DiseaseInfo disease = diseaseInfoMapper.selectOne(wrapper);
            if (disease != null) {
                results.add(formatDisease(disease));
            }
        }

        // 查询虫害
        if (!"disease".equals(category)) {
            LambdaQueryWrapper<PestInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PestInfo::getPestName, keyword);
            wrapper.last("LIMIT 1");

            PestInfo pest = pestInfoMapper.selectOne(wrapper);
            if (pest != null) {
                results.add(formatPest(pest));
            }
        }

        if (results.isEmpty()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "未找到相关病虫害信息");
            error.put("keyword", keyword);
            return objectMapper.writeValueAsString(error);
        }

        if (results.size() == 1) {
            return objectMapper.writeValueAsString(results.get(0));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", results.size());
        result.put("results", results);
        return objectMapper.writeValueAsString(result);
    }

    private String executeSearch(String keyword, String category) throws JsonProcessingException {
        List<Map<String, Object>> results = new ArrayList<>();
        String likePattern = "%" + keyword + "%";

        // 搜索病害
        if (!"pest".equals(category)) {
            LambdaQueryWrapper<DiseaseInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.and(w -> w
                    .like(DiseaseInfo::getNameCn, keyword)
                    .or().like(DiseaseInfo::getDiseaseName, keyword)
                    .or().like(DiseaseInfo::getDescription, keyword)
                    .or().like(DiseaseInfo::getConditions, keyword)
                    .or().like(DiseaseInfo::getPrevention, keyword)
            );
            wrapper.last("LIMIT 10");

            List<DiseaseInfo> diseases = diseaseInfoMapper.selectList(wrapper);
            diseases.forEach(d -> results.add(formatDisease(d)));
        }

        // 搜索虫害
        if (!"disease".equals(category)) {
            LambdaQueryWrapper<PestInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.and(w -> w
                    .like(PestInfo::getPestName, keyword)
                    .or().like(PestInfo::getDescription, keyword)
                    .or().like(PestInfo::getConditions, keyword)
                    .or().like(PestInfo::getPrevention, keyword)
            );
            wrapper.last("LIMIT 10");

            List<PestInfo> pests = pestInfoMapper.selectList(wrapper);
            pests.forEach(p -> results.add(formatPest(p)));
        }

        if (results.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", 0);
            result.put("results", Collections.emptyList());
            result.put("message", "未找到相关病虫害信息");
            return objectMapper.writeValueAsString(result);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", results.size());
        result.put("results", results);
        return objectMapper.writeValueAsString(result);
    }

    private Map<String, Object> formatDisease(DiseaseInfo d) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "disease");
        item.put("id", d.getId());
        item.put("name", d.getNameCn());
        item.put("nameEn", d.getDiseaseName());
        item.put("description", d.getDescription());
        item.put("conditions", d.getConditions());
        item.put("prevention", d.getPrevention());
        return item;
    }

    private Map<String, Object> formatPest(PestInfo p) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "pest");
        item.put("id", p.getId());
        item.put("name", p.getPestName());
        item.put("description", p.getDescription());
        item.put("conditions", p.getConditions());
        item.put("prevention", p.getPrevention());
        return item;
    }
}
