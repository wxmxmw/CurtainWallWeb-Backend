package com.company.curtainwall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_task_batches")
public class BizTaskBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private Long userId;

    private String name;

    private Integer totalCount;

    private Integer processedCount;

    private String status; // processing, done, partial_error

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
