package com.secondhand.product.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件存储实现。
 * 文件保存在 ~/.secondhand/uploads/ 下。
 * 自动生成 200x200 缩略图。
 * 支持 CDN 前缀配置。
 */
@Service
public class LocalStorageService implements StorageService {

    private final Path baseDir = Paths.get(System.getProperty("user.home"), ".secondhand", "uploads");

    @Value("${app.cdn-base-url:}")
    private String cdnBaseUrl;

    public LocalStorageService() {
        try { Files.createDirectories(baseDir); } catch (IOException ignored) {}
    }

    /** 拼接 CDN 前缀（如果配置了的话） */
    private String cdn(String path) {
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) return path;
        return cdnBaseUrl + path;
    }

    @Override
    public StoredFile store(MultipartFile file, String namespace) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().substring(0, 12) + ext;
            Path dir = baseDir.resolve(namespace);
            Files.createDirectories(dir);

            // 保存原图
            Path dest = dir.resolve(filename);
            file.transferTo(dest.toFile());

            // 生成缩略图 (200x200)
            String thumbName = filename.replace(ext, "_thumb" + ext);
            Path thumbDest = dir.resolve(thumbName);
            generateThumbnail(dest.toFile(), thumbDest.toFile(), 200, 200);

            String relPath = "/uploads/" + namespace.replace("\\", "/") + "/";
            return new StoredFile(
                    file.getOriginalFilename(),
                    cdn(relPath + filename),
                    cdn(relPath + thumbName),
                    file.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            Path p = baseDir.resolve(filePath.replaceFirst("^/uploads/", ""));
            Files.deleteIfExists(p);
            // 同时删除缩略图
            String thumbPath = p.toString().replaceFirst("(\\.\\w+)$", "_thumb$1");
            Files.deleteIfExists(Path.of(thumbPath));
        } catch (IOException ignored) {}
    }

    private void generateThumbnail(java.io.File source, java.io.File target, int w, int h) throws IOException {
        BufferedImage original = ImageIO.read(source);
        if (original == null) {
            // 不是图片，直接复制
            Files.copy(source.toPath(), target.toPath());
            return;
        }
        BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();
        ImageIO.write(thumb, "jpg", target);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        return switch (ext) {
            case ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp" -> ext;
            default -> ".jpg";
        };
    }
}
