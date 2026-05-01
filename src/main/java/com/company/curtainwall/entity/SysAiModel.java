package com.company.curtainwall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_models")
public class SysAiModel {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String modelKey;

    private String name;

    private String version;

    private String filePath;

    private Boolean isActive;

    private String description;

    private LocalDateTime createdAt;
}
