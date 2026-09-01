package com.seohamin.hisnetmobile.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SwaggerConfig {

    private final BuildProperties buildProperties;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo());
    }

    public Info apiInfo() {
        return new Info()
                .title("HISNetMobile")
                .description("히즈넷 모바일")
                .version(buildProperties.getVersion());
    }
}
