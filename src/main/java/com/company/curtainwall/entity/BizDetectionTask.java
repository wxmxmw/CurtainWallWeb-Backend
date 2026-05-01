package com.company.curtainwall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "biz_detection_tasks", autoResultMap = true)
public class BizDetectionTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobId;

    private Long batchId;

    private Long userId;

    private String modelKey;

    private String status; // queued, running, done, error

    private String inputImagePath;

    private String outputImagePath;

    private BigDecimal paramConf;

    private BigDecimal paramIou;

    private Integer paramImgsz;

    private Integer paramMaxDet;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode resultMetrics;

    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    public String getModelName() {
        if (this.modelKey != null && this.modelKey.contains("/")) {
            String[] parts = this.modelKey.split("/");
            // 逻辑：寻找 "runs" 后面的第一个部分作为模型名
            // 例如: runs/rust_yolo11s_train1/weights/best.pt -> rust_yolo11s_train1
            for (int i = 0; i < parts.length; i++) {
                if ("runs".equals(parts[i]) && i + 1 < parts.length) {
                    return parts[i + 1];
                }
            }
        }
        return this.modelKey; // 如果解析失败或不是那种格式，返回原始key
    }
}
