package com.agriculture.service;

import com.agriculture.modules.camera.mapper.CameraGridMapper;
import com.agriculture.modules.greenhouse.mapper.GreenhouseMapper;
import com.agriculture.modules.grid.mapper.GridMapper;
import com.agriculture.modules.greenhouse.dto.GreenhouseDTO;
import com.agriculture.modules.camera.entity.CameraGrid;
import com.agriculture.modules.greenhouse.entity.Greenhouse;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.greenhouse.service.impl.GreenhouseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GreenhouseService 单元测试")
class GreenhouseServiceTest {

    @Mock
    private GreenhouseMapper greenhouseMapper;

    @Mock
    private GridMapper gridMapper;

    @Mock
    private CameraGridMapper cameraGridMapper;

    @InjectMocks
    private GreenhouseServiceImpl greenhouseService;

    private Greenhouse mockGreenhouse;

    @BeforeEach
    void setUp() {
        // ServiceImpl 需要 baseMapper 注入
        ReflectionTestUtils.setField(greenhouseService, "baseMapper", greenhouseMapper);

        mockGreenhouse = new Greenhouse();
        mockGreenhouse.setId("gh-001");
        mockGreenhouse.setSectorId("GH-A1");
        mockGreenhouse.setCropSpecies("Solanum lycopersicum");
        mockGreenhouse.setPlantingDate(LocalDate.of(2026, 3, 15));
        mockGreenhouse.setLocation("34.2614N, 108.9423E");
        mockGreenhouse.setArea(new BigDecimal("2400.00"));
        mockGreenhouse.setStatus("ACTIVE");
        mockGreenhouse.setCompanyId("comp-001");
        mockGreenhouse.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
        mockGreenhouse.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
    }

    // ==================== 列表查询 ====================

    @Nested
    @DisplayName("列表查询")
    class ListGreenhouses {

        @Test
        @DisplayName("无条件查询")
        void listAll_returnsPage() {
            Page<Greenhouse> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockGreenhouse));
            when(greenhouseMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            IPage<Greenhouse> result = greenhouseService.listGreenhouses(null, null, 1, 20);

            assertEquals(1, result.getTotal());
            assertEquals("GH-A1", result.getRecords().get(0).getSectorId());
        }

        @Test
        @DisplayName("按状态筛选")
        void listByStatus() {
            Page<Greenhouse> page = new Page<>(1, 20, 0);
            page.setRecords(Collections.emptyList());
            when(greenhouseMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            IPage<Greenhouse> result = greenhouseService.listGreenhouses("INACTIVE", null, 1, 20);

            assertEquals(0, result.getTotal());
        }
    }

    // ==================== 详情查询 ====================

    @Nested
    @DisplayName("详情查询")
    class GetDetail {

        @Test
        @DisplayName("查询存在的温室")
        void detailExists_returnsGreenhouseWithCounts() {
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);
            when(gridMapper.selectCount(any())).thenReturn(3L);

            Grid grid1 = new Grid(); grid1.setId("g1");
            Grid grid2 = new Grid(); grid2.setId("g2");
            Grid grid3 = new Grid(); grid3.setId("g3");
            when(gridMapper.selectList(any()))
                    .thenReturn(List.of(grid1, grid2, grid3));
            when(cameraGridMapper.selectCount(any())).thenReturn(2L);

            Greenhouse result = greenhouseService.getGreenhouseDetail("gh-001");

            assertEquals("gh-001", result.getId());
            assertEquals(3, result.getGridCount());
            assertEquals(2, result.getCameraCount());
        }

        @Test
        @DisplayName("查询不存在的温室抛出异常")
        void detailNotExists_throwsException() {
            when(greenhouseMapper.selectById("not-exist")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> greenhouseService.getGreenhouseDetail("not-exist"));
            assertEquals(40112, ex.getCode());
        }

        @Test
        @DisplayName("无关联网格时 cameraCount 为 0")
        void detailNoGrids_cameraCountZero() {
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);
            when(gridMapper.selectCount(any())).thenReturn(0L);
            when(gridMapper.selectList(any()))
                    .thenReturn(Collections.emptyList());

            Greenhouse result = greenhouseService.getGreenhouseDetail("gh-001");

            assertEquals(0, result.getGridCount());
            assertEquals(0, result.getCameraCount());
        }
    }

    // ==================== 新增 ====================

    @Nested
    @DisplayName("新增温室")
    class CreateGreenhouse {

        @Test
        @DisplayName("成功新增温室")
        void createSuccess() {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("GH-A2");
            dto.setCropSpecies("番茄");
            dto.setStatus("ACTIVE");

            when(greenhouseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            doAnswer(invocation -> {
                Greenhouse entity = invocation.getArgument(0);
                entity.setId("gh-new-001");
                return 1;
            }).when(greenhouseMapper).insert(any(Greenhouse.class));

            String id = greenhouseService.createGreenhouse(dto, "comp-001");

            assertNotNull(id);
            assertEquals("gh-new-001", id);
            verify(greenhouseMapper).insert(any(Greenhouse.class));
        }

        @Test
        @DisplayName("区域编号已存在抛出异常")
        void createDuplicateSectorId_throwsException() {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("GH-A1");

            when(greenhouseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> greenhouseService.createGreenhouse(dto, "comp-001"));
            assertEquals(40110, ex.getCode());
        }

        @Test
        @DisplayName("区域编号为空抛出异常")
        void createEmptySectorId_throwsException() {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> greenhouseService.createGreenhouse(dto, "comp-001"));
            assertEquals(40111, ex.getCode());
        }
    }

    // ==================== 修改 ====================

    @Nested
    @DisplayName("修改温室")
    class UpdateGreenhouse {

        @Test
        @DisplayName("成功修改温室")
        void updateSuccess() {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setCropSpecies("玉米");

            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);
            when(greenhouseMapper.updateById(any(Greenhouse.class))).thenReturn(1);

            greenhouseService.updateGreenhouse("gh-001", dto);

            verify(greenhouseMapper).updateById(any(Greenhouse.class));
        }

        @Test
        @DisplayName("修改不存在的温室抛出异常")
        void updateNotExists_throwsException() {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setCropSpecies("玉米");

            when(greenhouseMapper.selectById("not-exist")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> greenhouseService.updateGreenhouse("not-exist", dto));
            assertEquals(40112, ex.getCode());
        }

        @Test
        @DisplayName("修改为已存在的 sectorId 抛出异常")
        void updateDuplicateSectorId_throwsException() {
            GreenhouseDTO dto = new GreenhouseDTO();
            dto.setSectorId("GH-B1");

            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);
            when(greenhouseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> greenhouseService.updateGreenhouse("gh-001", dto));
            assertEquals(40110, ex.getCode());
        }
    }

    // ==================== 删除 ====================

    @Nested
    @DisplayName("删除温室")
    class DeleteGreenhouse {

        @Test
        @DisplayName("成功删除无关联的温室")
        void deleteSuccess() {
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);
            when(gridMapper.selectCount(any())).thenReturn(0L);
            when(greenhouseMapper.deleteById("gh-001")).thenReturn(1);

            greenhouseService.deleteGreenhouse("gh-001");

            verify(greenhouseMapper).deleteById("gh-001");
        }

        @Test
        @DisplayName("删除不存在的温室抛出异常")
        void deleteNotExists_throwsException() {
            when(greenhouseMapper.selectById("not-exist")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> greenhouseService.deleteGreenhouse("not-exist"));
            assertEquals(40112, ex.getCode());
        }

        @Test
        @DisplayName("删除有关联网格的温室抛出异常")
        void deleteWithGrids_throwsException() {
            when(greenhouseMapper.selectById("gh-001")).thenReturn(mockGreenhouse);
            when(gridMapper.selectCount(any())).thenReturn(3L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> greenhouseService.deleteGreenhouse("gh-001"));
            assertEquals(40113, ex.getCode());
        }
    }
}
