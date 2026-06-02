package com.secondhand.product.category.repository;

import com.secondhand.product.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 查找所有一级分类 */
    List<Category> findByParentIdIsNullOrderBySortOrderAsc();

    /** 查找某个父分类下的子分类 */
    List<Category> findByParentIdOrderBySortOrderAsc(Long parentId);

    /** 查找所有子分类 ID（用于筛选时展开） */
    List<Category> findByParentIdIn(List<Long> parentIds);
}
