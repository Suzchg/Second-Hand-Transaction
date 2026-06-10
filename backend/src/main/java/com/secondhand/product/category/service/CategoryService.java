package com.secondhand.product.category.service;

import com.secondhand.product.category.entity.Category;
import com.secondhand.product.category.repository.CategoryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 分类服务。
 * 应用启动时自动创建种子数据。
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepo;

    public CategoryService(CategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    /** 获取完整分类树 */
    @Transactional(readOnly = true)
    public List<CategoryDto> getCategoryTree() {
        List<Category> roots = categoryRepo.findByParentIdIsNullOrderBySortOrderAsc();
        List<CategoryDto> tree = new ArrayList<>();
        for (Category root : roots) {
            List<Category> children = categoryRepo.findByParentIdOrderBySortOrderAsc(root.getId());
            tree.add(new CategoryDto(root, children));
        }
        return tree;
    }

    /** 获取所有分类的扁平列表（含子分类） */
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepo.findAll();
    }

    /** 获取某个分类及其所有子分类的 ID */
    public List<Long> getCategoryAndChildrenIds(Long categoryId) {
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        List<Category> children = categoryRepo.findByParentIdOrderBySortOrderAsc(categoryId);
        for (Category child : children) {
            ids.add(child.getId());
        }
        return ids;
    }

    /** 种子数据：仅一级分类 + emoji 图标。若存在旧二级分类（总数 > 25）则重建 */
    @PostConstruct
    @Transactional
    public void seedCategories() {
        // 新格式只有 23 个一级分类，旧格式包含二级分类（总数超 100）
        if (categoryRepo.count() <= 25 && categoryRepo.count() > 0) return;

        // 清除旧分类（含二级分类）
        categoryRepo.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        record Cat(String name, String icon) {}
        List<Cat> seed = List.of(
            new Cat("手机通讯", "📱"),
            new Cat("电脑办公", "💻"),
            new Cat("数码影音", "📷"),
            new Cat("家用电器", "🔌"),
            new Cat("家具家装", "🛋️"),
            new Cat("家居日用", "🏠"),
            new Cat("男装", "👔"),
            new Cat("女装", "👗"),
            new Cat("鞋靴箱包", "👟"),
            new Cat("珠宝配饰", "💍"),
            new Cat("美妆护肤", "💄"),
            new Cat("母婴亲子", "👶"),
            new Cat("运动户外", "⚽"),
            new Cat("图书音像", "📚"),
            new Cat("食品生鲜", "🍎"),
            new Cat("医药保健", "💊"),
            new Cat("汽车用品", "🚗"),
            new Cat("宠物生活", "🐾"),
            new Cat("文玩收藏", "🏺"),
            new Cat("乐器/音乐", "🎵"),
            new Cat("潮玩/模型", "🎯"),
            new Cat("游戏/电竞", "🎮"),
            new Cat("票券/其他", "🎫")
        );

        int sort = 1; // 0 留给"推荐"
        for (var c : seed) {
            Category cat = new Category();
            cat.setName(c.name());
            cat.setIconUrl(c.icon());
            cat.setParentId(null);
            cat.setSortOrder(sort++);
            cat.setCreatedAt(now);
            cat.setUpdatedAt(now);
            categoryRepo.save(cat);
        }
    }

    /** 分类 DTO（一棵子树） */
    public record CategoryDto(Long id, String name, String iconUrl, Integer sortOrder,
                              List<CategoryDto> children) {
        public CategoryDto(Category parent, List<Category> children) {
            this(parent.getId(), parent.getName(), parent.getIconUrl(), parent.getSortOrder(),
                 children.stream().map(c -> new CategoryDto(c, List.of())).toList());
        }
    }
}
