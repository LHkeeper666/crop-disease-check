package com.agriculture.service;

import com.agriculture.common.exception.BusinessException;
import com.agriculture.modules.grid.dto.GridCreateDTO;
import com.agriculture.modules.grid.dto.GridUpdateDTO;
import com.agriculture.modules.grid.entity.Grid;
import com.agriculture.modules.grid.mapper.GridMapper;
import com.agriculture.modules.grid.service.impl.GridServiceImpl;
import com.agriculture.modules.grid.vo.GridVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GridServiceImpl 单元测试")
class GridServiceImplTest {

    @Mock private GridMapper gridMapper;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private GridServiceImpl gridService;

    private Grid sampleGrid;

    @BeforeEach
    void setUp() {
        sampleGrid = new Grid();
        sampleGrid.setId("grid-001");
        sampleGrid.setLabel("A1");
        sampleGrid.setGreenhouseId("gh-001");
        sampleGrid.setCropType("番茄");
        sampleGrid.setCreatedAt(LocalDateTime.now());
        sampleGrid.setUpdatedAt(LocalDateTime.now());
        sampleGrid.setDeleted((byte) 0);
    }

    @Nested
    @DisplayName("listGrids - 网格列表")
    class ListGrids {

        @Test
        @DisplayName("无筛选返回全部")
        void listGrids_noFilter_returnsAll() {
            when(gridMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sampleGrid));

            List<GridVO> result = gridService.listGrids(null);

            assertEquals(1, result.size());
            assertEquals("A1", result.get(0).getLabel());
        }

        @Test
        @DisplayName("按温室筛选")
        void listGrids_withGreenhouseId_filtersByGreenhouse() {
            when(gridMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sampleGrid));

            List<GridVO> result = gridService.listGrids("gh-001");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("空列表返回空")
        void listGrids_empty_returnsEmpty() {
            when(gridMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<GridVO> result = gridService.listGrids(null);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("createGrid - 创建网格")
    class CreateGrid {

        @Test
        @DisplayName("创建成功返回ID")
        void createGrid_valid_returnsId() {
            when(gridMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(gridMapper.insert(any(Grid.class))).thenReturn(1);

            GridCreateDTO dto = new GridCreateDTO();
            dto.setLabel("B2");
            dto.setGreenhouseId("gh-001");
            dto.setCropType("黄瓜");

            String id = gridService.createGrid(dto);

            assertNotNull(id);
            verify(gridMapper).insert(any(Grid.class));
        }

        @Test
        @DisplayName("重复编号抛异常")
        void createGrid_duplicateLabel_throwsException() {
            when(gridMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            GridCreateDTO dto = new GridCreateDTO();
            dto.setLabel("A1");

            assertThrows(BusinessException.class, () -> gridService.createGrid(dto));
        }
    }

    @Nested
    @DisplayName("updateGrid - 更新网格")
    class UpdateGrid {

        @Test
        @DisplayName("更新成功")
        void updateGrid_valid_success() {
            when(gridMapper.selectById("grid-001")).thenReturn(sampleGrid);
            when(gridMapper.updateById(any(Grid.class))).thenReturn(1);

            GridUpdateDTO dto = new GridUpdateDTO();
            dto.setCropType("辣椒");

            gridService.updateGrid("grid-001", dto);

            assertEquals("辣椒", sampleGrid.getCropType());
            verify(gridMapper).updateById(sampleGrid);
        }

        @Test
        @DisplayName("不存在的网格抛异常")
        void updateGrid_notFound_throwsException() {
            when(gridMapper.selectById("not-exist")).thenReturn(null);

            GridUpdateDTO dto = new GridUpdateDTO();

            assertThrows(BusinessException.class,
                    () -> gridService.updateGrid("not-exist", dto));
        }

        @Test
        @DisplayName("修改为重复编号抛异常")
        void updateGrid_duplicateLabel_throwsException() {
            when(gridMapper.selectById("grid-001")).thenReturn(sampleGrid);
            when(gridMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            GridUpdateDTO dto = new GridUpdateDTO();
            dto.setLabel("B3");

            assertThrows(BusinessException.class,
                    () -> gridService.updateGrid("grid-001", dto));
        }
    }

    @Nested
    @DisplayName("deleteGrid - 删除网格")
    class DeleteGrid {

        @Test
        @DisplayName("逻辑删除成功")
        void deleteGrid_existing_success() {
            when(gridMapper.selectById("grid-001")).thenReturn(sampleGrid);
            when(gridMapper.updateById(any(Grid.class))).thenReturn(1);

            gridService.deleteGrid("grid-001");

            assertEquals((byte) 1, sampleGrid.getDeleted());
        }

        @Test
        @DisplayName("不存在的网格抛异常")
        void deleteGrid_notFound_throwsException() {
            when(gridMapper.selectById("not-exist")).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> gridService.deleteGrid("not-exist"));
        }
    }
}
