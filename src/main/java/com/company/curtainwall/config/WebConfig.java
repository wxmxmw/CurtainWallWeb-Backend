package com.company.curtainwall.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.image-location}")
    private String imageLocation;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resolvedImageLocation = imageLocation.endsWith("/") ? imageLocation : imageLocation + "/";
        registry.addResourceHandler("/images/**")
                .addResourceLocations(resolvedImageLocation);
    }
}
