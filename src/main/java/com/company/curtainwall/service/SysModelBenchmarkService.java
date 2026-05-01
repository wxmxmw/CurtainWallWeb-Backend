package com.company.curtainwall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.curtainwall.entity.SysModelBenchmark;

import java.util.List;
import java.util.Map;

public interface SysModelBenchmarkService extends IService<SysModelBenchmark> {
    List<Map<String, Object>> getBenchmarksWithModelInfo();
}
