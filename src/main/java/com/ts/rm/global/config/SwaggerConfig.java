package com.ts.rm.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // JWT 인증 스킴 정의
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        // 보안 요구사항 정의
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearer-jwt");

        return new OpenAPI()
                .info(apiInfo())
                .servers(
                        List.of(
                                // 상대 경로 사용 (현재 접속 중인 호스트/포트 자동 사용)
                                new Server()
                                        .url("/")
                                        .description("Current Server")))
                // JWT 인증 스킴 등록
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", securityScheme))
                // 전역 보안 요구사항 적용
                .addSecurityItem(securityRequirement);
    }

    /**
     * 태그 순서를 정의하는 커스터마이저
     * 컨트롤러에서 정의한 태그 정보를 유지하면서 순서만 재정렬합니다.
     */
    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            // 원하는 태그 순서 정의
            List<String> tagOrder = List.of(
                    "인증",
                    "계정",
                    "기본",
                    "릴리즈 버전",
                    "릴리즈 파일",
                    "패치",
                    "사이트",
                    "엔지니어",
                    "부서",
                    "리소스 파일",
                    "리소스 링크",
                    "작업",
                    "프로젝트",
                    "데이터 분석",
                    "터미널",
                    "서비스 관리",
                    "파일 동기화"
            );

            // 기존 태그 가져오기 (컨트롤러에서 정의된 태그들)
            List<Tag> existingTags = openApi.getTags();
            if (existingTags == null || existingTags.isEmpty()) {
                return;
            }

            // 태그를 Map으로 변환 (이름 -> Tag)
            java.util.Map<String, Tag> tagMap = existingTags.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Tag::getName,
                            tag -> tag,
                            (existing, replacement) -> existing // 중복 시 기존 태그 유지
                    ));

            // 정의된 순서대로 태그 재정렬
            List<Tag> sortedTags = new java.util.ArrayList<>();
            for (String tagName : tagOrder) {
                Tag tag = tagMap.get(tagName);
                if (tag != null) {
                    sortedTags.add(tag);
                    tagMap.remove(tagName); // 처리된 태그 제거
                }
            }

            // 순서에 없는 나머지 태그들 추가 (알파벳 순)
            tagMap.values().stream()
                    .sorted(java.util.Comparator.comparing(Tag::getName))
                    .forEach(sortedTags::add);

            openApi.setTags(sortedTags);
        };
    }

    private Info apiInfo() {
        return new Info()
                .title("Release Manager Documentation")
                .description("Release Manager REST API 문서")
                .version("v0.0.1")
                .contact(new Contact().name("Tscientific Dev#2 Team")
                        .email("jhlee@tscientific.co.kr"));
    }
}
