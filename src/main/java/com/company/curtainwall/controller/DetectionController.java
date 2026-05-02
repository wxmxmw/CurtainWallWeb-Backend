package com.company.curtainwall.controller;

import com.company.curtainwall.common.ApiResponse;
import com.company.curtainwall.entity.BizDetectionTask;
import com.company.curtainwall.service.BizDetectionTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
public class DetectionController {

    @Autowired
    private BizDetectionTaskService taskService;

    // =========================================================
    //  4. 锈蚀检测业务模块 (需展平返回，不使用 ApiResponse 包装)
    // =========================================================

    /**
     * 4.1 同步检测
     * 文档要求: 直接返回 image_base64, metrics 等字段在根对象
     */
    @PostMapping({"/api/corrosion/detect", "/detect"})
    public Map<String, Object> detectSync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("model") String model,
            @RequestParam(value = "conf", required = false) Double conf,
            @RequestParam(value = "iou", required = false) Double iou,
            @RequestParam(value = "imgsz", required = false) Integer imgsz,
            @RequestParam(value = "max_det", required = false) Integer maxDet) throws IOException {

        BigDecimal confBd = conf != null ? BigDecimal.valueOf(conf) : null;
        BigDecimal iouBd = iou != null ? BigDecimal.valueOf(iou) : null;

        BizDetectionTask result = taskService.processSyncDetection(file, model, confBd, iouBd, imgsz, maxDet);

        // 手动构建 Map 以实现扁平化结构
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        // 读取图片转 Base64
        String base64Str = "";
        if (result.getOutputImagePath() != null) {
            try {
                byte[] imageBytes = Files.readAllBytes(Paths.get(result.getOutputImagePath()));
                base64Str = Base64.getEncoder().encodeToString(imageBytes);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        response.put("image_base64", base64Str);
        response.put("metrics", result.getResultMetrics());

        Map<String, Object> params = new HashMap<>();
        params.put("model", result.getModelName());
        params.put("conf", result.getParamConf());
        response.put("params", params);

        return response;
    }

    /**
     * 4.2 提交异步任务
     * 文档要求: { "success": true, "job_id": "...", "status": "..." }
     */
    @PostMapping({"/api/corrosion/detect/enqueue", "/detect/enqueue"})
    public Map<String, Object> detectAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("model") String model,
            @RequestParam(value = "conf", required = false) Double conf,
            @RequestParam(value = "iou", required = false) Double iou,
            @RequestParam(value = "imgsz", required = false) Integer imgsz,
            @RequestParam(value = "max_det", required = false) Integer maxDet) throws IOException {

        BigDecimal confBd = conf != null ? BigDecimal.valueOf(conf) : null;
        BigDecimal iouBd = iou != null ? BigDecimal.valueOf(iou) : null;

        String jobId = taskService.enqueueTask(file, model, confBd, iouBd, imgsz, maxDet);

        // 扁平化返回
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("job_id", jobId);
        response.put("status", "pending");

        return response;
    }

    /**
     * 4.3 查询任务状态/结果
     */
    @GetMapping({"/api/corrosion/jobs/{jobId}", "/jobs/{jobId}"})
    public Map<String, Object> getJob(@PathVariable String jobId) {
        BizDetectionTask task = taskService.lambdaQuery()
                .eq(BizDetectionTask::getJobId, jobId)
                .one();

        Map<String, Object> response = new HashMap<>();
        if (task == null) {
            response.put("success", false);
            response.put("message", "Task not found");
            return response;
        }

        // 顶层字段
        // response.put("success", true); // 如果文档没要求顶层有success，这行可以注释掉
        response.put("job_id", task.getJobId());
        response.put("status", task.getStatus());

        if ("done".equals(task.getStatus())) {
            Map<String, Object> resultBody = new HashMap<>();
            resultBody.put("success", true);

            // 1. 补全 metrics
            resultBody.put("metrics", task.getResultMetrics());

            // 2. 补全 image_base64 (关键！)
            String base64Str = "";
            if (task.getOutputImagePath() != null) {
                try {
                    byte[] imageBytes = Files.readAllBytes(Paths.get(task.getOutputImagePath()));
                    base64Str = Base64.getEncoder().encodeToString(imageBytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            resultBody.put("image_base64", base64Str);

            // 3. 补全 params (关键！)
            Map<String, Object> params = new HashMap<>();
            params.put("model", task.getModelName());
            params.put("conf", task.getParamConf());
            params.put("iou", task.getParamIou());
            params.put("imgsz", task.getParamImgsz());
            params.put("max_det", task.getParamMaxDet());
            resultBody.put("params", params);

            response.put("result", resultBody);
        } else if ("error".equals(task.getStatus())) {
            response.put("message", task.getErrorMsg());
        }

        return response;
    }

    // =========================================================
    //  5. 批量检测业务 (文档要求嵌套 data，使用 ApiResponse)
    // =========================================================

    /**
     * 5.1 提交数据集检测
     * 文档要求: { "success": true, "data": { "batch_no": ... } }
     */
    @PostMapping({"/api/corrosion/detect/batch", "/detect/batch"})
    public ApiResponse<?> detectBatch(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "dataset_name", required = false) String datasetName,
            @RequestParam("model") String model,
            @RequestParam(value = "conf", required = false) Double conf,
            @RequestParam(value = "iou", required = false) Double iou) {
        try {
            BigDecimal confBd = conf != null ? BigDecimal.valueOf(conf) : null;
            BigDecimal iouBd = iou != null ? BigDecimal.valueOf(iou) : null;

            String batchNo = taskService.processBatchUpload(files, datasetName, model, confBd, iouBd);

            Map<String, Object> data = new HashMap<>();
            data.put("batch_no", batchNo);
            data.put("total_files", files.length);
            data.put("status", "processing");
            data.put("message", "已接收 " + files.length + " 张图片，开始后台处理");

            // 使用 ApiResponse 自动包裹 data 字段
            return ApiResponse.success(data);
        } catch (IOException e) {
            return ApiResponse.error("批量上传失败: " + e.getMessage());
        }
    }
}
