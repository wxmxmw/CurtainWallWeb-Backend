package com.company.curtainwall.controller;

import com.company.curtainwall.common.ApiResponse;
import com.company.curtainwall.entity.SysAiModel;
import com.company.curtainwall.service.SysAiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/corrosion/models")
@RequiredArgsConstructor
public class ModelController {

    private final SysAiModelService sysAiModelService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getModels() {
        List<SysAiModel> activeModels = sysAiModelService.getActiveModels();
        
        // Transform to API format
        List<Map<String, String>> modelList = activeModels.stream().map(model -> {
            Map<String, String> map = new HashMap<>();
            map.put("key", model.getModelKey());
            map.put("name", model.getName());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("models", modelList);
        
        return ApiResponse.success(data);
    }
}
