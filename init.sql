-- 创建数据库
CREATE DATABASE IF NOT EXISTS metal_corrosion_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE metal_corrosion_db;

-- 1. 用户表
CREATE TABLE `sys_users` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `role` VARCHAR(20) DEFAULT 'user' COMMENT '角色',
  `status` TINYINT(1) DEFAULT 1 COMMENT '1启用 0禁用',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB COMMENT='系统用户表';

-- 2. AI模型表
CREATE TABLE `sys_ai_models` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `model_key` VARCHAR(100) NOT NULL COMMENT 'API调用Key',
  `name` VARCHAR(100) NOT NULL COMMENT '展示名称',
  `file_path` VARCHAR(255) COMMENT '权重文件路径',
  `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key` (`model_key`)
) ENGINE=InnoDB COMMENT='AI模型配置表';

-- 3. 性能基准表
CREATE TABLE `sys_model_benchmarks` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `model_key` VARCHAR(100) NOT NULL,
  `map50` DECIMAL(5,4) DEFAULT 0,
  `map5095` DECIMAL(5,4) DEFAULT 0,
  `precision_val` DECIMAL(5,4) DEFAULT 0,
  `recall_val` DECIMAL(5,4) DEFAULT 0,
  `fps` INT DEFAULT 0,
  `latency_ms` DECIMAL(8,2) DEFAULT 0,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_model_key` (`model_key`)
) ENGINE=InnoDB COMMENT='模型性能基准表';

-- 4. 检测批次表 (New)
CREATE TABLE `biz_task_batches` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `batch_no` VARCHAR(64) NOT NULL COMMENT '批次业务编号',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `name` VARCHAR(100) COMMENT '数据集名称',
  `total_count` INT DEFAULT 0 COMMENT '总图片数',
  `processed_count` INT DEFAULT 0 COMMENT '已处理数',
  `status` VARCHAR(20) DEFAULT 'processing' COMMENT 'processing/done/partial_error',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_user_batch` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='检测批次表';

-- 5. 检测任务表 (Modified)
CREATE TABLE `biz_detection_tasks` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `job_id` VARCHAR(64) NOT NULL COMMENT '业务任务ID',
  `batch_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '所属批次ID，NULL为单次任务',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `model_key` VARCHAR(100) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'queued' COMMENT 'queued/running/done/error',
  `input_image_path` VARCHAR(512) NOT NULL COMMENT '原图路径',
  `output_image_path` VARCHAR(512) COMMENT '结果图路径',
  `param_conf` DECIMAL(4,2) DEFAULT 0.25,
  `param_iou` DECIMAL(4,2) DEFAULT 0.45,
  `param_imgsz` INT DEFAULT 640,
  `param_max_det` INT DEFAULT 300,
  `result_metrics` JSON COMMENT '检测结果JSON',
  `error_msg` TEXT COMMENT '错误信息',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `finished_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_id` (`job_id`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='检测任务流水表';