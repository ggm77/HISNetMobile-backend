package com.seohamin.hisnetmobile.domain.graduation.controller;

import com.seohamin.hisnetmobile.domain.graduation.dto.GraduationResponseDto;
import com.seohamin.hisnetmobile.domain.graduation.service.GraduationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Graduation", description = "졸업심사 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/graduation")
public class GraduationController {

    private final GraduationService graduationService;

    // 현재 로그인한 사용자의 졸업심사 결과 조회 API (항목별 판정 + 최종 졸업판정)
    @Operation(
            summary = "내 졸업심사 결과 조회",
            description = "졸업심사 결과 페이지(HGRA120M.php → PGRA123S*.php)를 파싱해 항목별 판정과 최종 졸업판정을 반환한다. "
                    + "졸업 확정 후 등 원본에서 조회할 수 없는 상태면 available=false 로 응답한다."
    )
    @GetMapping
    public ResponseEntity<GraduationResponseDto> getMyResult(
            @AuthenticationPrincipal final UserDetails userDetails
    ) {

        return ResponseEntity.ok(graduationService.getMyResult(userDetails));
    }
}
