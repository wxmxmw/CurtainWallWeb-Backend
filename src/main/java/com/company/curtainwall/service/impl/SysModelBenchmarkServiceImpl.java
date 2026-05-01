package com.company.curtainwall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.curtainwall.entity.SysAiModel;
import com.company.curtainwall.entity.SysModelBenchmark;
import com.company.curtainwall.mapper.SysModelBenchmarkMapper;
import com.company.curtainwall.service.SysAiModelService;
import com.company.curtainwall.service.SysModelBenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysModelBenchmarkServiceImpl extends ServiceImpl<SysModelBenchmarkMapper, SysModelBenchmark> implements SysModelBenchmarkService {

    private final SysAiModelService sysAiModelService;

    @Override
    public List<Map<String, Object>> getBenchmarksWithModelInfo() {
        List<SysModelBenchmark> benchmarks = this.list();
        List<SysAiModel> models = sysAiModelService.list();
        
        Map<String, String> modelNameMap = models.stream()
                .collect(Collectors.toMap(SysAiModel::getModelKey, SysAiModel::getName));

        List<Map<String, Object>> result = new ArrayList<>();
        
        for (SysModelBenchmark bm : benchmarks) {
            Map<String, Object> map = new HashMap<>();
            map.put("model", bm.getModelKey());
            // If model name exists in sys_ai_models, use it; otherwise use model_key or default
            map.put("name", modelNameMap.getOrDefault(bm.getModelKey(), bm.getModelKey()));
            map.put("map50", bm.getMap50());
            map.put("map5095", bm.getMap5095());
            map.put("precision", bm.getPrecisionVal()); // API says "precision", entity has "precisionVal"
            map.put("recall", bm.getRecallVal());       // API says "recall", entity has "recallVal"
            map.put("fps", bm.getFps());
            map.put("latency_ms", bm.getLatencyMs());
            
            result.add(map);
        }
        
        return result;
    }
}
