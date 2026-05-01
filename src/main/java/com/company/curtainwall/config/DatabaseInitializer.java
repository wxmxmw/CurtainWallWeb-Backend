package com.company.curtainwall.config;

import com.company.curtainwall.entity.SysAiModel;
import com.company.curtainwall.service.SysAiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final SysAiModelService sysAiModelService;

    @Override
    public void run(String... args) throws Exception {
        checkAndAddLastLoginAt();
        checkAndAddSysAiModelColumns();
        initDefaultModels();
    }

    private void checkAndAddLastLoginAt() {
        try {
            log.info("Checking if last_login_at column exists in sys_users table...");

            // Check if column exists
            String checkSql = "SELECT count(*) FROM information_schema.COLUMNS " +
                    "WHERE (TABLE_SCHEMA = 'metal_corrosion_db' OR TABLE_SCHEMA = DATABASE()) " +
                    "AND TABLE_NAME = 'sys_users' AND COLUMN_NAME = 'last_login_at'";

            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class);

            if (count != null && count == 0) {
                log.info("Column last_login_at missing in sys_users. Adding it...");
                String alterSql = "ALTER TABLE sys_users ADD COLUMN last_login_at DATETIME DEFAULT NULL COMMENT '最后登录时间'";
                jdbcTemplate.execute(alterSql);
                log.info("Column last_login_at added successfully.");
            } else {
                log.info("Column last_login_at already exists.");
            }
        } catch (Exception e) {
            log.error("Error checking/adding last_login_at column: {}", e.getMessage());
        }
    }

    private void checkAndAddSysAiModelColumns() {
        try {
            log.info("Checking columns in sys_ai_models table...");

            // Check for 'description'
            String checkDescSql = "SELECT count(*) FROM information_schema.COLUMNS " +
                    "WHERE (TABLE_SCHEMA = 'metal_corrosion_db' OR TABLE_SCHEMA = DATABASE()) " +
                    "AND TABLE_NAME = 'sys_ai_models' AND COLUMN_NAME = 'description'";

            Integer countDesc = jdbcTemplate.queryForObject(checkDescSql, Integer.class);

            if (countDesc != null && countDesc == 0) {
                log.info("Column description missing in sys_ai_models. Adding it...");
                String alterSql = "ALTER TABLE sys_ai_models ADD COLUMN description TEXT DEFAULT NULL COMMENT '模型描述/适用场景备注'";
                jdbcTemplate.execute(alterSql);
                log.info("Column description added successfully.");
            }

            // Check for 'version'
            String checkVerSql = "SELECT count(*) FROM information_schema.COLUMNS " +
                    "WHERE (TABLE_SCHEMA = 'metal_corrosion_db' OR TABLE_SCHEMA = DATABASE()) " +
                    "AND TABLE_NAME = 'sys_ai_models' AND COLUMN_NAME = 'version'";

            Integer countVer = jdbcTemplate.queryForObject(checkVerSql, Integer.class);

            if (countVer != null && countVer == 0) {
                log.info("Column version missing in sys_ai_models. Adding it...");
                String alterSql = "ALTER TABLE sys_ai_models ADD COLUMN version VARCHAR(20) DEFAULT 'v1.0' COMMENT '模型版本号' AFTER name";
                jdbcTemplate.execute(alterSql);
                log.info("Column version added successfully.");
            }

        } catch (Exception e) {
            log.error("Error checking/adding columns to sys_ai_models: {}", e.getMessage());
        }
    }

    private void initDefaultModels() {
        try {
            // Only add models if they don't exist (by key)
            // We'll check existence one by one or just skip if table not empty?
            // User said "add all other models", so we should probably add them even if
            // table has some.
            // But to be safe and simple, we check existence by key.

            log.info("Initializing AI models...");

            addModelIfMissing(
                    "yolo11n.pt",
                    "YOLO11n (Nano Base)",
                    "v1.0",
                    "runs/yolo11n.pt/yolo11n.pt",
                    true,
                    "超轻量级基础模型，速度最快，适合实时检测。");

            addModelIfMissing(
                    "yolo11s.pt",
                    "YOLO11s (Small Base)",
                    "v1.0",
                    "runs/yolo11s.pt/yolo11s.pt",
                    true,
                    "小型基础模型，平衡了速度与精度。");

            // Trained YOLO11n Models
            addModelIfMissing(
                    "rust_yolo11n_train1",
                    "YOLO11n (Rust Train 1)",
                    "v1.0",
                    "runs/rust_yolo11n_train1/weights/best.pt",
                    true,
                    "基于YOLO11n在Rust数据集上的第一轮训练模型。");

            // rust_yolo11n_train2 - folder exists but checking weight file.
            // Assuming structure ../runs/rust_yolo11n_train2/weights/best.pt based on
            // typical YOLO
            // Wait, user provided LS list, rust_yolo11n_train2/weights/best.pt exists.
            addModelIfMissing(
                    "rust_yolo11n_train2",
                    "YOLO11n (Rust Train 2)",
                    "v1.0",
                    "runs/rust_yolo11n_train2/weights/best.pt",
                    true,
                    "基于YOLO11n在Rust数据集上的第二轮训练模型，可能包含参数调整。");

            // Trained YOLO11s Models
            addModelIfMissing(
                    "rust_yolo11s_train1",
                    "YOLO11s (Rust Train 1)",
                    "v1.0",
                    "runs/rust_yolo11s_train1/weights/best.pt",
                    true,
                    "基于YOLO11s在Rust数据集上的第一轮训练模型。");

            addModelIfMissing(
                    "rust_yolo11s_train2",
                    "YOLO11s (Rust Train 2)",
                    "v1.0",
                    "runs/rust_yolo11s_train2/weights/best.pt",
                    true,
                    "基于YOLO11s在Rust数据集上的第二轮训练模型。");

            // Preprocessing Experiments
            addModelIfMissing(
                    "rust_yolo11s_train_preproc",
                    "YOLO11s (Preproc v1)",
                    "v1.0",
                    "runs/rust_yolo11s_train_preproc/weights/best.pt",
                    true,
                    "基于YOLO11s，应用了图像预处理增强训练。");

            addModelIfMissing(
                    "rust_yolo11s_train_preproc_v2",
                    "YOLO11s (Preproc v2)",
                    "v2.0",
                    "runs/rust_yolo11s_train_preproc_v2/weights/best.pt",
                    true,
                    "基于YOLO11s，应用了改进的图像预处理（v2）增强训练。");

            // Segmentation
            addModelIfMissing(
                    "rust_seg_v2",
                    "Rust Segmentation v2",
                    "v2.0",
                    "runs/rust_seg_v2/weights/best.pt",
                    true,
                    "锈蚀分割模型 v2，支持像素级锈蚀区域检测。");

            addModelIfMissing(
                    "deeplab_train",
                    "DeepLab v3+",
                    "v1.0",
                    "runs/deeplab_train/best.pth",
                    true, // Enabled support for DeepLab
                    "DeepLab语义分割模型，用于高精度锈蚀区域分割。");

            // Classification / Regression
            addModelIfMissing(
                    "rust_reg_classification",
                    "Rust Regression/Class",
                    "v1.0",
                    "runs/rust_reg_classification/best.pt",
                    true,
                    "锈蚀程度回归/分类模型。");

            log.info("All AI models initialization check completed.");

        } catch (Exception e) {
            log.error("Error initializing default models: {}", e.getMessage());
        }
    }

    private void addModelIfMissing(String key, String name, String version, String path, boolean active, String desc) {
        SysAiModel existing = sysAiModelService
                .getOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiModel>()
                        .eq(SysAiModel::getModelKey, key));

        if (existing == null) {
            SysAiModel model = new SysAiModel();
            model.setModelKey(key);
            model.setName(name);
            model.setVersion(version);
            model.setFilePath(path);
            model.setIsActive(active);
            model.setDescription(desc);
            model.setCreatedAt(LocalDateTime.now());
            sysAiModelService.save(model);
            log.info("Added model: {}", name);
        } else {
            // Auto-update active status if changed in code
            if (existing.getIsActive() != active) {
                existing.setIsActive(active);
                sysAiModelService.updateById(existing);
                log.info("Updated model active status: {} -> {}", name, active);
            }
            log.info("Model already exists: {}", name);
        }
    }
}
