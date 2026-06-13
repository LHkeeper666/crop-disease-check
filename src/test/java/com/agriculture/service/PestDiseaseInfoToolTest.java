package com.agriculture.service;

import com.agriculture.modules.agriBrain.tool.impl.PestDiseaseInfoTool;
import com.agriculture.modules.pestDiseaseInfo.entity.DiseaseInfo;
import com.agriculture.modules.pestDiseaseInfo.entity.PestInfo;
import com.agriculture.modules.pestDiseaseInfo.mapper.DiseaseInfoMapper;
import com.agriculture.modules.pestDiseaseInfo.mapper.PestInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PestDiseaseInfoToolTest {

    @Mock
    private DiseaseInfoMapper diseaseInfoMapper;

    @Mock
    private PestInfoMapper pestInfoMapper;

    @InjectMocks
    private PestDiseaseInfoTool pestDiseaseInfoTool;

    private DiseaseInfo createDisease(Integer id, String nameCn, String diseaseName) {
        DiseaseInfo d = new DiseaseInfo();
        d.setId(id);
        d.setNameCn(nameCn);
        d.setDiseaseName(diseaseName);
        d.setDescription("叶片出现白色粉状物");
        d.setConditions("高温高湿");
        d.setPrevention("使用三唑酮");
        return d;
    }

    private PestInfo createPest(Integer id, String pestName) {
        PestInfo p = new PestInfo();
        p.setId(id);
        p.setPestName(pestName);
        p.setDescription("吸食植物汁液");
        p.setConditions("温暖干燥");
        p.setPrevention("使用吡虫啉");
        return p;
    }

    @Nested
    @DisplayName("name action")
    class NameAction {

        @Test
        @DisplayName("按中文名称查询病害")
        void name_diseaseByName_returnsDiseaseInfo() {
            DiseaseInfo disease = createDisease(0, "番茄晚疫病", "Tomato Late Blight");
            when(diseaseInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(disease);
            when(pestInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            String result = pestDiseaseInfoTool.execute(Map.of("action", "name", "keyword", "番茄晚疫病"), "user-001", "company-001");

            assertTrue(result.contains("番茄晚疫病"));
            assertTrue(result.contains("三唑酮"));
        }

        @Test
        @DisplayName("按名称查询虫害")
        void name_pestByName_returnsPestInfo() {
            PestInfo pest = createPest(22, "蚜虫");
            when(diseaseInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(pestInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pest);

            String result = pestDiseaseInfoTool.execute(Map.of("action", "name", "keyword", "蚜虫"), "user-001", "company-001");

            assertTrue(result.contains("蚜虫"));
            assertTrue(result.contains("吡虫啉"));
        }

        @Test
        @DisplayName("名称不匹配时返回错误")
        void name_notFound_returnsError() {
            when(diseaseInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(pestInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            String result = pestDiseaseInfoTool.execute(Map.of("action", "name", "keyword", "不存在的病害"), "user-001", "company-001");

            assertTrue(result.contains("error"));
        }

        @Test
        @DisplayName("指定category只查病害")
        void name_categoryDisease_onlyQueriesDisease() {
            DiseaseInfo disease = createDisease(0, "白粉病", "Powdery Mildew");
            when(diseaseInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(disease);

            String result = pestDiseaseInfoTool.execute(Map.of("action", "name", "keyword", "白粉病", "category", "disease"), "user-001", "company-001");

            assertTrue(result.contains("白粉病"));
        }
    }

    @Nested
    @DisplayName("search action")
    class SearchAction {

        @Test
        @DisplayName("按关键词模糊搜索")
        void search_keyword_returnsMatchingResults() {
            DiseaseInfo disease = createDisease(0, "白粉病", "Powdery Mildew");
            when(diseaseInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(disease));
            when(pestInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            String result = pestDiseaseInfoTool.execute(Map.of("action", "search", "keyword", "白色粉状"), "user-001", "company-001");

            assertTrue(result.contains("\"total\":1"));
            assertTrue(result.contains("白粉病"));
        }

        @Test
        @DisplayName("无搜索结果")
        void search_noMatch_returnsEmpty() {
            when(diseaseInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(pestInfoMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            String result = pestDiseaseInfoTool.execute(Map.of("action", "search", "keyword", "xyznotexist"), "user-001", "company-001");

            assertTrue(result.contains("\"total\":0"));
        }

        @Test
        @DisplayName("关键词为空时返回错误")
        void search_emptyKeyword_returnsError() {
            String result = pestDiseaseInfoTool.execute(Map.of("action", "search", "keyword", ""), "user-001", "company-001");

            assertTrue(result.contains("error"));
        }
    }
}
