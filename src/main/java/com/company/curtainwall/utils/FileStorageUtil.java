package com.company.curtainwall.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class FileStorageUtil {

    // 根目录名称
    private static final String ROOT_DIR_NAME = "images";
    private static final String UPLOAD_SUB_DIR = "uploads";
    private static final String OUTPUT_SUB_DIR = "outputs";

    /**
     * 保存文件
     */
    public String saveFile(MultipartFile file, Long userId) throws IOException {
        if (file == null || file.isEmpty()) return null;

        // 1. 获取项目物理根目录 (例如 D:\Projects\MyCode)
        String projectRoot = System.getProperty("user.dir");

        // 2. 构建【物理路径】用于写入文件
        Path physicalDir = Paths.get(projectRoot, ROOT_DIR_NAME, String.valueOf(userId), UPLOAD_SUB_DIR);

        // 确保物理目录存在
        if (!Files.exists(physicalDir)) {
            Files.createDirectories(physicalDir);
        }

        // 生成文件名
        String filename = UUID.randomUUID().toString().replace("-", "") + "_" + file.getOriginalFilename();

        // 3. 写入文件到硬盘 (必须用物理绝对路径)
        Path physicalFilePath = physicalDir.resolve(filename);
        Files.write(physicalFilePath, file.getBytes());

        // 4. 构建【相对路径】用于返回 (不包含 projectRoot)
        Path relativePath = Paths.get(ROOT_DIR_NAME, String.valueOf(userId), UPLOAD_SUB_DIR, filename);

        // 统一转换为正斜杠
        return relativePath.toString().replace(File.separator, "/");
    }

    /**
     * 生成输出路径
     * 输出相对路径: images/101/outputs/abc_detected.png
     * 同时会在硬盘上物理创建 outputs 文件夹
     */
    public String generateOutputPath(String inputRelativePathStr) {
        // 1. 获取项目物理根目录
        String projectRoot = System.getProperty("user.dir");

        Path inputRelativePath = Paths.get(inputRelativePathStr);
        String filename = inputRelativePath.getFileName().toString();

        // 处理文件名: abc.jpg -> abc_detected.png
        int lastDot = filename.lastIndexOf('.');
        String nameWithoutExt = (lastDot == -1) ? filename : filename.substring(0, lastDot);
        String newFilename = nameWithoutExt + "_detected.png";

        // 从相对路径中解析 userId
        // inputPath: images/101/uploads/file.jpg
        // parent: images/101/uploads
        // parent.parent: images/101
        // parent.parent.filename: 101
        Path userDir = inputRelativePath.getParent().getParent();
        String userId = userDir.getFileName().toString();

        // 2. 构建【物理路径】用于创建文件夹 (防止Python报错)
        // 物理路径: D:\Projects\MyCode\images\101\outputs
        Path physicalOutputDir = Paths.get(projectRoot, ROOT_DIR_NAME, userId, OUTPUT_SUB_DIR);

        try {
            if (!Files.exists(physicalOutputDir)) {
                Files.createDirectories(physicalOutputDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("无法创建输出目录: " + physicalOutputDir.toString());
        }

        // 3. 构建【相对路径】用于返回
        // 结果: images/101/outputs/abc_detected.png
        Path relativeOutputPath = Paths.get(ROOT_DIR_NAME, userId, OUTPUT_SUB_DIR, newFilename);

        // 统一转换为正斜杠
        return relativeOutputPath.toString().replace(File.separator, "/");
    }
}