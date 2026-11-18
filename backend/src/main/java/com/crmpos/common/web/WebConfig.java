package com.crmpos.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 우리 컨트롤러의 주소에 /api/v1을 붙인다. /actuator, /swagger-ui 같은 것은 그대로 둔다 (03-api.md 1장). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                type -> type.isAnnotationPresent(RestController.class)
                        && type.getPackageName().startsWith("com.crmpos"));
    }
}
