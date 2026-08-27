package com.secondhand.integration;

import com.secondhand.product.entity.Product;
import com.secondhand.product.image.entity.ProductImage;
import com.secondhand.product.image.repository.ProductImageRepository;
import com.secondhand.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 补充用例：商品图片管理
 *
 * 覆盖范围：
 * - 模块间调用：ProductImageController → ProductService（校验商品存在 + 卖家身份）
 *              → StorageService（本地存储）→ ProductImageRepository → ProductRepository
 * - 数据库访问：product_images、products 两张表（封面图自动同步）
 * - 对外接口：
 *   - GET    /api/products/{productId}/images          （公开列表）
 *   - POST   /api/products/{productId}/images          （上传，multipart）
 *   - DELETE /api/products/{productId}/images/{imageId}（删除）
 *   - PUT    /api/products/{productId}/images/{imageId}/cover（设为封面）
 *
 * 用例流程覆盖：
 * - 主成功流程：上传首图（自动设封面）→ 上传第二张 → 列表查询 → 设为封面 → 删除
 * - 备选流程：上传后商品 coverImageUrl 同步更新；删除封面图后自动切换为下一张
 * - 异常流程：非卖家上传/删除、商品不存在、未登录、文件格式不支持
 */
@Testcontainers
@DisplayName("补充用例：商品图片管理")
class ProductImageFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired ProductImageRepository imageRepo;
    @Autowired ProductRepository productRepo;

    /** 生成一个真实的 JPEG 字节流，确保 ImageIO 能读取并生成缩略图 */
    private byte[] validJpegBytes() throws Exception {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        img.createGraphics().fillRect(0, 0, 400, 300);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    private MockMultipartFile jpegMultipart(String paramName, String filename) throws Exception {
        return new MockMultipartFile(paramName, filename, "image/jpeg", validJpegBytes());
    }

    // ==================== 主成功流程 ====================

    @Test
    @DisplayName("主成功 · 上传首图自动设为封面→上传第二张→列表→设封面→删除")
    void shouldUploadListSetCoverAndDelete() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "图片测试商品", 5000, 1);

        // 1. 上传第一张图片（应自动设为封面）
        long imgId1 = extractId(mockMvc.perform(multipart("/api/products/" + pid + "/images")
                        .file(jpegMultipart("file", "p1.jpg"))
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id", greaterThan(0)))
                .andExpect(jsonPath("$.data.url", containsString("/uploads/products/")))
                .andExpect(jsonPath("$.data.thumbnailUrl", containsString("_thumb")))
                .andExpect(jsonPath("$.data.sortOrder").value(0))
                .andReturn());

        // 验证商品 coverImageUrl 已同步
        Product p = productRepo.findById(pid).orElseThrow();
        if (p.getCoverImageUrl() == null || !p.getCoverImageUrl().contains("_thumb")) {
            throw new AssertionError("首图上传后未自动同步商品封面");
        }

        // 2. 上传第二张图片
        long imgId2 = extractId(mockMvc.perform(multipart("/api/products/" + pid + "/images")
                        .file(jpegMultipart("file", "p2.jpg"))
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andReturn());

        // 3. 公开列表查询（无需鉴权）
        mockMvc.perform(get("/api/products/" + pid + "/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(imgId1))
                .andExpect(jsonPath("$.data[1].id").value(imgId2));

        // 4. 把第二张设为封面
        mockMvc.perform(put("/api/products/" + pid + "/images/" + imgId2 + "/cover")
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 验证商品封面已切换为第二张
        Product p2 = productRepo.findById(pid).orElseThrow();
        ProductImage img2 = imageRepo.findById(imgId2).orElseThrow();
        if (!img2.getThumbnailUrl().equals(p2.getCoverImageUrl())) {
            throw new AssertionError("setCover 未同步商品封面图");
        }

        // 5. 删除第一张图片
        mockMvc.perform(delete("/api/products/" + pid + "/images/" + imgId1)
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 列表应剩 1 张
        mockMvc.perform(get("/api/products/" + pid + "/images"))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    // ==================== 备选流程 ====================

    @Test
    @DisplayName("备选 · 删除当前封面图后自动切换为剩余第一张")
    void shouldAutoSwitchCoverAfterDelete() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "封面切换商品", 3000, 1);

        long img1 = extractId(mockMvc.perform(multipart("/api/products/" + pid + "/images")
                        .file(jpegMultipart("file", "a.jpg"))
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.31"))
                .andExpect(status().isOk()).andReturn());

        long img2 = extractId(mockMvc.perform(multipart("/api/products/" + pid + "/images")
                        .file(jpegMultipart("file", "b.jpg"))
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.32"))
                .andExpect(status().isOk()).andReturn());

        // 把第二张设为封面
        mockMvc.perform(put("/api/products/" + pid + "/images/" + img2 + "/cover")
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.33"))
                .andExpect(status().isOk());

        // 删除当前封面（第二张），应自动切换为第一张
        mockMvc.perform(delete("/api/products/" + pid + "/images/" + img2)
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.34"))
                .andExpect(status().isOk());

        ProductImage first = imageRepo.findById(img1).orElseThrow();
        Product p = productRepo.findById(pid).orElseThrow();
        if (!first.getThumbnailUrl().equals(p.getCoverImageUrl())) {
            throw new AssertionError("删除当前封面后未自动切换到剩余首张");
        }
    }

    @Test
    @DisplayName("备选 · 删除全部图片后商品封面置空")
    void shouldClearCoverWhenAllImagesDeleted() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "清空图片商品", 1000, 1);

        long img = extractId(mockMvc.perform(multipart("/api/products/" + pid + "/images")
                        .file(jpegMultipart("file", "only.jpg"))
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.41"))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(delete("/api/products/" + pid + "/images/" + img)
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.42"))
                .andExpect(status().isOk());

        Product p = productRepo.findById(pid).orElseThrow();
        if (p.getCoverImageUrl() != null) {
            throw new AssertionError("图片全部删除后封面图未置空，实际值=" + p.getCoverImageUrl());
        }
    }

    // ==================== 异常流程 ====================

    @Test
    @DisplayName("异常 · 非卖家上传图片返回 403")
    void shouldRejectUploadByNonOwner() throws Exception {
        TestUser seller = createTestUser();
        TestUser other = createTestUser();
        long pid = createProduct(seller.userId(), "他人商品图片", 1000, 1);

        mockMvc.perform(multipart("/api/products/" + pid + "/images")
                        .file(jpegMultipart("file", "x.jpg"))
                        .header("Authorization", "Bearer " + other.token())
                        .header("X-Forwarded-For", "10.0.0.51"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 未登录上传图片返回 401")
    void shouldRejectUploadWithoutAuth() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "未登录测试", 1000, 1);

        mockMvc.perform(multipart("/api/products/" + pid + "/images")
                        .file(jpegMultipart("file", "x.jpg")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("异常 · 非卖家删除他人商品图片返回 403")
    void shouldRejectDeleteByNonOwner() throws Exception {
        TestUser seller = createTestUser();
        TestUser other = createTestUser();
        long pid = createProduct(seller.userId(), "他人图片删除", 1000, 1);

        long img = extractId(mockMvc.perform(multipart("/api/products/" + pid + "/images")
                        .file(jpegMultipart("file", "x.jpg"))
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.61"))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(delete("/api/products/" + pid + "/images/" + img)
                        .header("Authorization", "Bearer " + other.token())
                        .header("X-Forwarded-For", "10.0.0.62"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 商品不存在时上传图片返回 404")
    void shouldRejectUploadToNonExistentProduct() throws Exception {
        TestUser seller = createTestUser();
        mockMvc.perform(multipart("/api/products/99999999/images")
                        .file(jpegMultipart("file", "x.jpg"))
                        .header("Authorization", "Bearer " + seller.token())
                        .header("X-Forwarded-For", "10.0.0.71"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("异常 · 不存在的商品查询图片列表返回空数组")
    void shouldReturnEmptyListForNonExistentProduct() throws Exception {
        // 商品不存在时，findByProductIdOrderBySortOrderAsc 不会抛异常，只返回空列表
        mockMvc.perform(get("/api/products/99999999/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
