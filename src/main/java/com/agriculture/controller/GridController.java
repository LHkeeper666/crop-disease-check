package com.agriculture.controller;

import com.agriculture.annotation.RequireRole;
import com.agriculture.dto.GridCreateDTO;
import com.agriculture.dto.GridUpdateDTO;
import com.agriculture.service.GridService;
import com.agriculture.vo.GridVO;
import com.agriculture.vo.Result;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网格区域控制器
 */
@RestController
@RequestMapping("/api/grid")
@RequiredArgsConstructor
public class GridController {

    private final GridService gridService;

    /**
     * 网格列表
     */
    @GetMapping("/list")
    public Result<List<GridVO>> listGrids(@RequestParam(required = false) String greenhouseId) {
        List<GridVO> voList = gridService.listGrids(greenhouseId);
        return Result.success(voList);
    }

    /**
     * 新增网格
     */
    @PostMapping
    @RequireRole("ADMIN")
    public Result<String> createGrid(@Valid @RequestBody GridCreateDTO dto) {
        String id = gridService.createGrid(dto);
        return Result.success("网格创建成功", id);
    }

    /**
     * 修改网格
     */
    @PutMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> updateGrid(@PathVariable String id, @RequestBody GridUpdateDTO dto) {
        gridService.updateGrid(id, dto);
        return Result.success("网格更新成功", null);
    }

    /**
     * 删除网格
     */
    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    public Result<Void> deleteGrid(@PathVariable String id) {
        gridService.deleteGrid(id);
        return Result.success("网格删除成功", null);
    }
}
