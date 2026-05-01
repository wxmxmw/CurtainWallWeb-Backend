package com.company.curtainwall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 【关键修改】由于我们现在运行在 Docker 容器内，图片都挂载到了容器内部的 /app/images/ 目录下
        String imagePath = "file:/app/images/";

        // 当访问 /images/** 时，去本地的 imagePath 找文件
        registry.addResourceHandler("/images/**")
                .addResourceLocations(imagePath);
    }
}