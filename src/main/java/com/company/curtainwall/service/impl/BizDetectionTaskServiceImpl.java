package com.company.curtainwall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.curtainwall.entity.BizDetectionTask;
import com.company.curtainwall.entity.BizTaskBatch;
import com.company.curtainwall.entity.SysAiModel;
import com.company.curtainwall.mapper.BizDetectionTaskMapper;
import com.company.curtainwall.mapper.BizTaskBatchMapper;
import com.company.curtainwall.service.BizDetectionTaskService;
import com.company.curtainwall.service.BizTaskBatchService;
import com.company.curtainwall.service.SysAiModelService;
import com.company.curtainwall.utils.FileStorageUtil;
import com.company.curtainwall.utils.PythonInferenceUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BizDetectionTaskServiceImpl extends ServiceImpl<BizDetectionTaskMapper, BizDetectionTask>
        implements BizDetectionTaskService {

    @Autowired
    private FileStorageUtil fileStorageUtil;

    @Autowired
    private PythonInferenceUtil pythonInferenceUtil;

    @Autowired
    private BizTaskBatchService batchService;

    @Autowired
    @Lazy
    private BizDetectionTaskService self;

    @Autowired
    private BizTaskBatchMapper batchMapper;

    @Autowired
    private SysAiModelService sysAiModelService;

    // 【配置】模型文件的基础目录（请修改为你实际存放 .pt 文件的文件夹）
    // Windows 例子: "D:/project_data/models/"
    // Linux 例子: "/app/data/models/"
    private static final String MODEL_BASE_DIR = "models" + File.separator;

    // =========================================================
    // 获取当前登录用户ID
    // =========================================================
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

    // 辅助方法：智能解析模型路径
    private String resolveModelPath(String modelKey) {
        // 1. 如果用户传的就是绝对路径，直接返回
        if (modelKey.contains("/") || modelKey.contains("\\")) {
            return modelKey;
        }

        // 2. 尝试从数据库查找映射
        SysAiModel sysAiModel = sysAiModelService.getOne(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getModelKey, modelKey)
                .last("LIMIT 1"));

        if (sysAiModel != null && sysAiModel.getFilePath() != null) {
            return sysAiModel.getFilePath();
        }

        // 3. 【智能兜底】数据库没找到？去默认文件夹找 models/modelKey.pt
        String potentialPath = MODEL_BASE_DIR + modelKey + ".pt";
        if (Files.exists(Paths.get(potentialPath))) {
            return Paths.get(potentialPath).toAbsolutePath().toString();
        }

        // 4. 还没找到，就按原样扔给 Python (可能会报错)
        return modelKey;
    }

    private BizDetectionTask buildBaseTask(String model, String path, BigDecimal conf, BigDecimal iou, Integer imgsz,
            Integer maxDet, Long userId) {
        BizDetectionTask task = new BizDetectionTask();
        task.setJobId("job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        task.setUserId(userId);
        task.setModelKey(model);
        task.setInputImagePath(path);
        task.setParamConf(conf != null ? conf : BigDecimal.valueOf(0.25));
        task.setParamIou(iou != null ? iou : BigDecimal.valueOf(0.45));
        task.setParamImgsz(imgsz != null ? imgsz : 640);
        task.setParamMaxDet(maxDet != null ? maxDet : 300);
        task.setCreatedAt(LocalDateTime.now());
        return task;
    }

    @Override
    public BizDetectionTask processSyncDetection(MultipartFile file, String model, BigDecimal conf, BigDecimal iou,
            Integer imgsz, Integer maxDet) throws IOException {
        Long userId = getCurrentUserId();

        // 1. 使用带 userId 的保存逻辑
        String inputPath = fileStorageUtil.saveFile(file, userId);
        String outputPath = fileStorageUtil.generateOutputPath(inputPath);

        BizDetectionTask task = buildBaseTask(model, inputPath, conf, iou, imgsz, maxDet, userId);

        try {
            // 2. 解析真实模型路径
            String realModelPath = resolveModelPath(model);

            JsonNode resultNode = pythonInferenceUtil.runInference(
                    inputPath,
                    realModelPath, // 传给 Python 的是绝对路径
                    outputPath,
                    task.getParamConf(),
                    task.getParamIou(),
                    task.getParamImgsz(),
                    task.getParamMaxDet());

            if (resultNode.has("success") && resultNode.get("success").asBoolean()) {
                task.setStatus("done");
                task.setResultMetrics(resultNode.get("metrics"));
                task.setOutputImagePath(outputPath);
            } else {
                task.setStatus("error");
                task.setErrorMsg(resultNode.has("error") ? resultNode.get("error").asText() : "Unknown error");
            }
        } catch (Exception e) {
            task.setStatus("error");
            task.setErrorMsg(e.getMessage());
            e.printStackTrace();
        }

        task.setFinishedAt(LocalDateTime.now());
        this.save(task);
        return task;
    }

    @Override
    public String enqueueTask(MultipartFile file, String model, BigDecimal conf, BigDecimal iou, Integer imgsz,
            Integer maxDet) throws IOException {
        Long userId = getCurrentUserId();
        // 使用带 userId 的保存
        String inputPath = fileStorageUtil.saveFile(file, userId);

        BizDetectionTask task = buildBaseTask(model, inputPath, conf, iou, imgsz, maxDet, userId);
        task.setStatus("queued");
        this.save(task);

        self.processTaskAsync(task);

        return task.getJobId();
    }

    @Override
    @Async
    public void processTaskAsync(BizDetectionTask task) {
        try {
            // 使用工具类生成路径
            String outputPath = fileStorageUtil.generateOutputPath(task.getInputImagePath());

            // 解析路径
            String realModelPath = resolveModelPath(task.getModelKey());

            JsonNode resultNode = pythonInferenceUtil.runInference(
                    task.getInputImagePath(),
                    realModelPath,
                    outputPath,
                    task.getParamConf(),
                    task.getParamIou(),
                    task.getParamImgsz(),
                    task.getParamMaxDet());

            if (resultNode.has("success") && resultNode.get("success").asBoolean()) {
                task.setStatus("done");
                task.setResultMetrics(resultNode.get("metrics"));
                task.setOutputImagePath(outputPath);
            } else {
                task.setStatus("error");
                task.setErrorMsg(resultNode.has("error") ? resultNode.get("error").asText() : "Unknown Python error");
            }
        } catch (Exception e) {
            task.setStatus("error");
            task.setErrorMsg(e.getMessage());
        }
        task.setFinishedAt(LocalDateTime.now());
        this.updateById(task);

        // 批量更新逻辑 - 优化为原子更新防止并发计数丢失
        if (task.getBatchId() != null) {
            // 1. 原子递增 processed_count
            UpdateWrapper<BizTaskBatch> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", task.getBatchId());
            updateWrapper.setSql("processed_count = processed_count + 1");
            batchMapper.update(null, updateWrapper);

            // 2. 检查是否需要更新状态为 done
            BizTaskBatch batch = batchMapper.selectById(task.getBatchId());
            if (batch != null && batch.getProcessedCount() >= batch.getTotalCount()) {
                if (!"done".equals(batch.getStatus())) {
                    BizTaskBatch statusUpdate = new BizTaskBatch();
                    statusUpdate.setId(batch.getId());
                    statusUpdate.setStatus("done");
                    batchMapper.updateById(statusUpdate);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String processBatchUpload(MultipartFile[] files, String datasetName, String model, BigDecimal conf,
            BigDecimal iou) throws IOException {
        Long userId = getCurrentUserId();

        BizTaskBatch batch = new BizTaskBatch();
        String batchNo = "batch_" + System.currentTimeMillis();
        batch.setBatchNo(batchNo);
        batch.setUserId(userId);
        batch.setName(datasetName != null && !datasetName.isEmpty() ? datasetName : "Dataset " + batchNo);
        batch.setTotalCount(files.length);
        batch.setProcessedCount(0);
        batch.setStatus("processing");
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batchService.save(batch);

        List<BizDetectionTask> tasks = new ArrayList<>();
        for (MultipartFile file : files) {
            // 循环内带 userId 保存
            String path = fileStorageUtil.saveFile(file, userId);
            BizDetectionTask task = buildBaseTask(model, path, conf, iou, 640, 300, userId);
            task.setBatchId(batch.getId());
            task.setStatus("queued");
            tasks.add(task);
        }
        this.saveBatch(tasks);

        for (BizDetectionTask task : tasks) {
            self.processTaskAsync(task);
        }

        return batchNo;
    }
}