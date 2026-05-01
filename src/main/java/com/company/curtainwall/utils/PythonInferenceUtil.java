package com.company.curtainwall.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class PythonInferenceUtil {

    // 可以在 application.yml 中配置 python 路径，默认 "python"
    @Value("${app.python.path:python}")
    private String pythonPath;

    @Value("${app.python.script:scripts/inference.py}")
    private String scriptPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 Python 脚本执行推理
     */
    public JsonNode runInference(String imagePath, String modelKey, String outputPath,
            BigDecimal conf, BigDecimal iou, Integer imgsz, Integer maxDet) {
        try {
            // 获取绝对路径
            File scriptFile = new File(scriptPath);
            File imgFile = new File(imagePath);
            // 假设模型都在 runs 目录或者项目根目录下，根据 key 寻找真实路径
            // 这里简单处理：如果 key 是相对路径，假设它在项目根目录
            File modelFile = new File(modelKey);
            File outFile = new File(outputPath);

            // 确保输出目录存在
            outFile.getParentFile().mkdirs();

            List<String> commands = new ArrayList<>();
            commands.add(pythonPath);
            commands.add(scriptFile.getAbsolutePath());
            commands.add("--image");
            commands.add(imgFile.getAbsolutePath());
            commands.add("--model");
            commands.add(modelFile.getAbsolutePath());
            commands.add("--output");
            commands.add(outFile.getAbsolutePath());

            if (conf != null) {
                commands.add("--conf");
                commands.add(conf.toString());
            }
            if (iou != null) {
                commands.add("--iou");
                commands.add(iou.toString());
            }
            if (imgsz != null) {
                commands.add("--imgsz");
                commands.add(imgsz.toString());
            }
            if (maxDet != null) {
                commands.add("--max_det");
                commands.add(maxDet.toString());
            }

            ProcessBuilder pb = new ProcessBuilder(commands);
            // 设置工作目录为脚本所在目录，以便 import deeplabv3plus 能正常工作
            pb.directory(scriptFile.getParentFile());
            pb.redirectErrorStream(true); // 合并错误流到输出流

            Process process = pb.start();

            // 读取 Python 输出
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            String lastJsonLine = null;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                // 假设最后一行有效的输出是 JSON
                if (line.trim().startsWith("{") && line.trim().endsWith("}")) {
                    lastJsonLine = line;
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Python script exited with code " + exitCode + "\nOutput: " + output);
            }

            if (lastJsonLine == null) {
                throw new RuntimeException("No JSON output from Python script. Output: " + output);
            }

            return objectMapper.readTree(lastJsonLine);

        } catch (Exception e) {
            throw new RuntimeException("Inference failed: " + e.getMessage(), e);
        }
    }
}