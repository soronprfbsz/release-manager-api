package com.ts.rm.domain.menu.controller;

import com.ts.rm.domain.menu.dto.MenuDto.MenuResponse;
import com.ts.rm.global.response.ApiResponse;
import com.ts.rm.global.response.SwaggerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

/**
 * MenuController Swagger 문서화 인터페이스
 */
@Tag(name = "기본", description = "공통 코드, 메뉴 등 솔루션 기본 API")
@SwaggerResponse
public interface MenuControllerDocs {

    @Operation(
            summary = "메뉴 목록 조회",
            description = "현재 로그인한 사용자의 권한에 따라 접근 가능한 메뉴를 계층 구조로 조회합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MenuListResponse.class),
                            examples = @ExampleObject(
                                    name = "메뉴 목록 조회 성공 예시",
                                    value = """
                                            {
                                              "status": "success",
                                              "data": [
                                                {
                                                  "menuId": "version_management",
                                                  "menuName": "버전 관리",
                                                  "children": [
                                                    {
                                                      "menuId": "version_standard",
                                                      "menuName": "Standard",
                                                      "children": []
                                                    },
                                                    {
                                                      "menuId": "version_custom",
                                                      "menuName": "Custom",
                                                      "children": []
                                                    }
                                                  ]
                                                },
                                                {
                                                  "menuId": "patch_management",
                                                  "menuName": "패치 관리",
                                                  "children": [
                                                    {
                                                      "menuId": "patch_standard",
                                                      "menuName": "Standard",
                                                      "children": []
                                                    },
                                                    {
                                                      "menuId": "patch_custom",
                                                      "menuName": "Custom",
                                                      "children": []
                                                    }
                                                  ]
                                                },
                                                {
                                                  "menuId": "operation_management",
                                                  "menuName": "운영 관리",
                                                  "children": [
                                                    {
                                                      "menuId": "operation_site",
                                                      "menuName": "사이트",
                                                      "children": []
                                                    },
                                                    {
                                                      "menuId": "operation_engineer",
                                                      "menuName": "엔지니어",
                                                      "children": []
                                                    }
                                                  ]
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
    )
    ResponseEntity<ApiResponse<List<MenuResponse>>> getMenus();

    /**
     * Swagger 스키마용 wrapper 클래스 - 메뉴 목록
     */
    @Schema(description = "메뉴 목록 API 응답")
    class MenuListResponse {
        @Schema(description = "응답 상태", example = "success")
        public String status;

        @Schema(description = "메뉴 목록 (계층 구조)")
        public List<MenuResponse> data;
    }
}
