package com.seohamin.hisnetmobile.domain.auth.controller;

import com.seohamin.hisnetmobile.domain.auth.dto.AuthMeResponseDto;
import com.seohamin.hisnetmobile.domain.auth.dto.LoginRequestDto;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import com.seohamin.hisnetmobile.global.security.HisnetUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "세션 로그인 API")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * 로그인 엔드포인트 — 실제 처리는 시큐리티 로그인 필터가 하며 이 메서드 본문은 실행되지 않는다.
     * form-urlencoded 로 username/password 를 보내면 성공 시 204 + JSESSIONID 쿠키, 실패 시 401.
     */
    @Operation(
            summary = "세션 로그인",
            description = "application/x-www-form-urlencoded 로 username/password 전송. "
                    + "성공 시 204 No Content 와 함께 JSESSIONID 세션 쿠키가 발급된다. 실패 시 401."
    )
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> login(@ModelAttribute final LoginRequestDto request) {

        return ResponseEntity.noContent().build();
    }

    /**
     * 로그아웃 엔드포인트 — 실제 처리는 시큐리티 로그아웃 필터가 한다. 서버 세션만 폐기하며 원본 세션은 만료를 기다린다.
     */
    @Operation(summary = "로그아웃", description = "서버 세션(JSESSIONID)을 폐기한다. 성공 시 204.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {

        return ResponseEntity.noContent().build();
    }

    /**
     * 현재 세션의 로그인 사용자 정보를 반환하는 API. 프론트의 로그인 상태 확인용.
     */
    @Operation(summary = "현재 로그인 사용자 조회", description = "로그인 상태면 사용자 정보를, 아니면 401.")
    @GetMapping("/me")
    public ResponseEntity<AuthMeResponseDto> me(
            @AuthenticationPrincipal final HisnetUserDetails userDetails
    ) {
        if (userDetails == null) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }

        return ResponseEntity.ok(new AuthMeResponseDto(userDetails.getUsername()));
    }
}