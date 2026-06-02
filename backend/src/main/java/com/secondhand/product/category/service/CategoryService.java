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

    /** 种子数据：应用启动时若分类表为空则自动填充 */
    @PostConstruct
    @Transactional
    public void seedCategories() {
        if (categoryRepo.count() > 0) return;

        LocalDateTime now = LocalDateTime.now();
        Map<String, List<String>> seed = new LinkedHashMap<>();
        seed.put("数码电子", List.of("手机", "电脑/笔记本", "平板", "相机/摄影", "耳机/音箱", "智能穿戴"));
        seed.put("服装配饰", List.of("男装", "女装", "鞋靴", "包包", "珠宝首饰", "手表"));
        seed.put("图书音像", List.of("教材/教辅", "小说文学", "考试考证", "杂志期刊", "儿童绘本"));
        seed.put("生活家居", List.of("家具", "家电", "厨具餐具", "家纺家饰", "清洁日用"));
        seed.put("运动户外", List.of("健身器材", "球类运动", "骑行装备", "露营/钓鱼", "运动鞋服"));
        seed.put("其他", List.of("母婴用品", "美妆护肤", "玩具/乐器", "文玩收藏", "卡券/其他"));

        int rootSort = 0;
        for (var entry : seed.entrySet()) {
            Category root = new Category();
            root.setName(entry.getKey());
            root.setParentId(null);
            root.setSortOrder(rootSort++);
            root.setCreatedAt(now);
            root.setUpdatedAt(now);
            Category saved = categoryRepo.save(root);

            int childSort = 0;
            for (String childName : entry.getValue()) {
                Category child = new Category();
                child.setName(childName);
                child.setParentId(saved.getId());
                child.setSortOrder(childSort++);
                child.setCreatedAt(now);
                child.setUpdatedAt(now);
                categoryRepo.save(child);
            }
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
