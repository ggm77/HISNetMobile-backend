package com.seohamin.hisnetmobile.domain.timetable.controller;

import com.seohamin.hisnetmobile.domain.timetable.dto.TimetableResponseDto;
import com.seohamin.hisnetmobile.domain.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Timetable", description = "시간표 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/timetable")
public class TimetableController {

    private final TimetableService timetableService;

    // 현재 로그인한 사용자의 이번 학기 시간표 조회 API
    @Operation(
            summary = "내 시간표 조회",
            description = "원본 내시간표조회(HLES110M.php)를 파싱해 과목 단위로 반환한다. 연속 교시는 한 슬롯으로 합쳐진다."
    )
    @GetMapping
    public ResponseEntity<TimetableResponseDto> getMyTimetable(
            @AuthenticationPrincipal final UserDetails userDetails
    ) {

        return ResponseEntity.ok(timetableService.getMyTimetable(userDetails));
    }
}
