package com.exe.astratarot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Phục vụ file người dùng tải lên (hiện chỉ có avatar) qua /uploads/**.
 *
 * Lưu trên đĩa máy chủ là đủ cho môi trường phát triển. Khi lên production và
 * chạy nhiều instance thì phải chuyển sang object storage (S3/R2), vì mỗi
 * instance có đĩa riêng nên ảnh tải lên ở máy này máy kia sẽ không thấy.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(root.toUri().toString())
                .setCachePeriod(3600);
    }
}
