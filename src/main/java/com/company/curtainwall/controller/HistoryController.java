package com.company.curtainwall.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.curtainwall.common.ApiResponse;
import com.company.curtainwall.entity.BizDetectionTask;
import com.company.curtainwall.entity.BizTaskBatch;
import com.company.curtainwall.service.BizDetectionTaskService;
import com.company.curtainwall.service.BizTaskBatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
// 【关键修改1】类路径改为公共父路径 /corrosion
// 这样我们才能在方法里灵活定义后面是接 /history 还是直接接 /batch
@RequestMapping("/api/corrosion")
public class HistoryController {

    @Autowired
    private BizDetectionTaskService taskService;

    @Autowired
    private BizTaskBatchService batchService;

    private Long getCurrentUserId() {
        try {
            Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
            if (details instanceof Long) {
                return (Long) details;
            }
        } catch (Exception e) {
            // ignore
        }
        throw new RuntimeException("用户未登录或Token无效");
    }

    /**
     * 6.1 获取检测历史列表
     * 这里的路径拼接为: /corrosion + /history = /corrosion/history
     * (保持原有功能路径不变)
     */
    // 【关键修改2】明确指定 /history
    @GetMapping("/history")
    public ApiResponse<?> getHistoryList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "all") String type) {

        Long userId = getCurrentUserId();

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("page", page);
        List<Map<String, Object>> allRecords = new ArrayList<>();

        if ("batch".equals(type) || "all".equals(type)) {
            List<BizTaskBatch> batches = batchService.lambdaQuery()
                    .eq(BizTaskBatch::getUserId, userId)
                    .orderByDesc(BizTaskBatch::getCreatedAt)
                    .list();

            for (BizTaskBatch batch : batches) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", "batch");
                map.put("batch_no", batch.getBatchNo());
                map.put("name", batch.getName());
                map.put("status", batch.getStatus());
                map.put("total_count", batch.getTotalCount());
                map.put("processed_count", batch.getProcessedCount());
                map.put("created_at", batch.getCreatedAt());
                List<String> thumbUrls = taskService.lambdaQuery()
                        .eq(BizDetectionTask::getBatchId, batch.getId())
                        .select(BizDetectionTask::getInputImagePath)
                        .last("LIMIT 3")
                        .list()
                        .stream()
                        .map(t -> "/" + t.getInputImagePath().replace("\\", "/"))
                        .collect(Collectors.toList());

                map.put("thumbnail_urls", thumbUrls);
                allRecords.add(map);
            }
        }

        if ("single".equals(type) || "all".equals(type)) {
            List<BizDetectionTask> tasks = taskService.lambdaQuery()
                    .eq(BizDetectionTask::getUserId, userId)
                    .isNull(BizDetectionTask::getBatchId)
                    .orderByDesc(BizDetectionTask::getCreatedAt)
                    .list();

            for (BizDetectionTask task : tasks) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", "single");
                map.put("job_id", task.getJobId());
                map.put("status", task.getStatus());
                map.put("model", task.getModelName());
                map.put("created_at", task.getCreatedAt());
                if (task.getResultMetrics() != null) {
                    map.put("result_preview", task.getResultMetrics());
                }
                allRecords.add(map);
            }
        }

        allRecords.sort((o1, o2) -> {
            String t1 = o1.get("created_at").toString();
            String t2 = o2.get("created_at").toString();
            return t2.compareTo(t1);
        });

        int total = allRecords.size();
        int fromIndex = (page - 1) * limit;
        int toIndex = Math.min(fromIndex + limit, total);

        List<Map<String, Object>> pagedList = new ArrayList<>();
        if (fromIndex < total) {
            pagedList = allRecords.subList(fromIndex, toIndex);
        }

        responseData.put("total", total);
        responseData.put("list", pagedList);

        return ApiResponse.success(responseData);
    }

    /**
     * 6.2 获取批次详情
     * 【关键修改3】同时兼容两种路径格式！
     * 格式1 (你报错的那个): /corrosion/history/batches/{batchNo}
     * 格式2 (你提到的另一个): /corrosion/batch/{batchNo}
     */
    @GetMapping({
            "/batch/{batchNo}",           // 对应 /corrosion/batch/...
            "/history/batches/{batchNo}"  // 对应 /corrosion/history/batches/...
    })
    public ApiResponse<?> getBatchDetails(
            @PathVariable String batchNo,
            @RequestParam(required = false) String status) {

        Long userId = getCurrentUserId();

        BizTaskBatch batch = batchService.lambdaQuery()
                .eq(BizTaskBatch::getBatchNo, batchNo)
                .eq(BizTaskBatch::getUserId, userId)
                .one();

        if (batch == null) {
            return ApiResponse.error("未找到指定批次");
        }

        QueryWrapper<BizDetectionTask> taskQuery = new QueryWrapper<>();
        taskQuery.eq("batch_id", batch.getId());

        if (status != null && !status.isEmpty()) {
            taskQuery.eq("status", status);
        }

        List<BizDetectionTask> taskList = taskService.list(taskQuery);

        Map<String, Object> batchInfo = new HashMap<>();
        batchInfo.put("batch_no", batch.getBatchNo());
        batchInfo.put("name", batch.getName());
        batchInfo.put("status", batch.getStatus());
        batchInfo.put("progress", batch.getProcessedCount() + "/" + batch.getTotalCount());
        batchInfo.put("created_at", batch.getCreatedAt());

        List<Map<String, Object>> tasks = taskList.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("job_id", t.getJobId());

            String inputPath = t.getInputImagePath();
            map.put("input_image", inputPath != null ? "/" + inputPath.replace("\\", "/") : null);

            String outputPath = t.getOutputImagePath();
            map.put("output_image", outputPath != null ? "/" + outputPath.replace("\\", "/") : null);

            map.put("status", t.getStatus());

            if (t.getResultMetrics() != null) {
                map.put("metrics", t.getResultMetrics());
            }
            if (t.getErrorMsg() != null) {
                map.put("error_msg", t.getErrorMsg());
            }
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("batch_info", batchInfo);
        data.put("tasks", tasks);

        return ApiResponse.success(data);
    }
}