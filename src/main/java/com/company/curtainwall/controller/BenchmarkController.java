package com.company.curtainwall.controller;

import com.company.curtainwall.common.ApiResponse;
import com.company.curtainwall.service.SysModelBenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/corrosion/benchmarks", "/benchmarks"})
@RequiredArgsConstructor
public class BenchmarkController {

    private final SysModelBenchmarkService sysModelBenchmarkService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getBenchmarks() {
        List<Map<String, Object>> benchmarks = sysModelBenchmarkService.getBenchmarksWithModelInfo();
        return ApiResponse.success(benchmarks);
    }
}
