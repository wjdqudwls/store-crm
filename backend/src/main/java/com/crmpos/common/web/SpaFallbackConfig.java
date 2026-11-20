package com.crmpos.common.web;

import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 화면(Vue)을 Spring Boot가 정적 파일로 같이 내보낸다 (ADR-013). 화면 경로(/customers/12 등)에서 새로고침해도
 * 화면이 열리도록, 파일이 없고 확장자도 없는 경로는 index.html을 돌려준다.
 * API·actuator·Swagger 경로와 확장자가 있는 경로(없는 .js 등)는 돌려주지 않고 그대로 404로 둔다.
 */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    static final List<String> NOT_SPA_PREFIXES = List.of("api/", "actuator", "swagger-ui", "v3/");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/")
                .resourceChain(true).addResolver(new SpaResolver());
    }

    static final class SpaResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (!isSpaRoute(resourcePath)) {
                return null;
            }
            Resource index = new ClassPathResource("static/index.html");
            return index.exists() && index.isReadable() ? index : null;
        }

        static boolean isSpaRoute(String path) {
            String p = path.startsWith("/") ? path.substring(1) : path;
            if (NOT_SPA_PREFIXES.stream().anyMatch(prefix -> p.startsWith(prefix) || p.equals(prefix.replace("/", "")))) {
                return false;
            }
            String last = p.substring(p.lastIndexOf('/') + 1);
            return !last.contains(".");
        }
    }
}
