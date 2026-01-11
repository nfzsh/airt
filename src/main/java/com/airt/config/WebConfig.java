package com.airt.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web MVC 配置
 * 用于提供前端静态文件访问
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 添加 frontend 目录作为静态资源
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/frontend/", "file:./frontend/")
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(String resourcePath, org.springframework.core.io.Resource location) throws IOException {
                        org.springframework.core.io.Resource resource = location.createRelative(resourcePath);
                        return resource.exists() && resource.isReadable() ? resource : null;
                    }
                });
    }
}