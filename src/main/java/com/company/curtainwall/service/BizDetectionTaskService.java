package com.company.curtainwall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.curtainwall.entity.BizDetectionTask;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

public interface BizDetectionTaskService extends IService<BizDetectionTask> {

    // 同步检测
    BizDetectionTask processSyncDetection(MultipartFile file, String model, BigDecimal conf, BigDecimal iou,
            Integer imgsz, Integer maxDet) throws IOException;

    // 异步任务入队
    String enqueueTask(MultipartFile file, String model, BigDecimal conf, BigDecimal iou, Integer imgsz, Integer maxDet)
            throws IOException;

    // 批量上传
    String processBatchUpload(MultipartFile[] files, String datasetName, String model, BigDecimal conf, BigDecimal iou)
            throws IOException;

    void processTaskAsync(BizDetectionTask task);
}
