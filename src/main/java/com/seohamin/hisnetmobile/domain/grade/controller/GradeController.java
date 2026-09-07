package com.seohamin.hisnetmobile.domain.grade.controller;

import com.seohamin.hisnetmobile.domain.grade.dto.GradeResponseDto;
import com.seohamin.hisnetmobile.domain.grade.service.GradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Grade", description = "성적 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/grades")
public class GradeController {

    private final GradeService gradeService;

    // 현재 로그인한 사용자의 전체 성적 조회 API (누적 요약 + 학기별 상세)
    @Operation(
            summary = "내 성적 조회",
            description = "원본 전체성적조회(HREC110M.php)를 파싱해 총 누적 성적과 학기별 성적(과목별 등급 포함)을 반환한다. "
                    + "현학기 성적은 성적 입력·정정이 끝나야 원본에 반영된다."
    )
    @GetMapping
    public ResponseEntity<GradeResponseDto> getMyGrades(
            @AuthenticationPrincipal final UserDetails userDetails
    ) {

        return ResponseEntity.ok(gradeService.getMyGrades(userDetails));
    }
}
