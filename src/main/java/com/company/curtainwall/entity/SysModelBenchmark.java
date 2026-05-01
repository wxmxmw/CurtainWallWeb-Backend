package com.company.curtainwall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_model_benchmarks")
public class SysModelBenchmark {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String modelKey;

    private BigDecimal map50;

    private BigDecimal map5095;

    private BigDecimal precisionVal;

    private BigDecimal recallVal;

    private Integer fps;

    private BigDecimal latencyMs;

    private LocalDateTime createdAt;
}
