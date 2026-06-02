package com.secondhand.product.image;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象接口。
 * 当前使用本地文件存储，后续可切换 OSS/S3。
 */
public interface StorageService {

    /** 存储文件，返回访问路径信息 */
    StoredFile store(MultipartFile file, String namespace);

    /** 删除文件 */
    void delete(String filePath);

    /** 存储结果 */
    record StoredFile(String originalFilename, String storedPath, String thumbnailPath, long size) {}
}
