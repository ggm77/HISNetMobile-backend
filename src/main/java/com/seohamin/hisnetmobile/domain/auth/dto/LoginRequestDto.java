package com.seohamin.hisnetmobile.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 세션 로그인 요청 바디 (application/x-www-form-urlencoded).
 * <p>
 * 실제 처리는 스프링 시큐리티의 로그인 필터가 하며, 이 DTO 는 문서화(Swagger)용이다.
 * 자격증명은 서버에 저장/로깅되지 않고 원본 로그인 중계에만 사용된다.
 */
public record LoginRequestDto(

        @Schema(description = "원본(HISNet) 로그인 아이디 (학번)", example = "22000000")
        String username,

        @Schema(description = "원본(HISNet) 로그인 비밀번호 (평문 전송, 저장 안 함)")
        String password
) { }