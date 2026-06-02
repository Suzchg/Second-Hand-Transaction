package com.secondhand.product.category.controller;

import com.secondhand.common.ApiResponse;
import com.secondhand.product.category.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 获取分类树 */
    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.ok(categoryService.getCategoryTree());
    }
}
