package com.seohamin.hisnetmobile.domain.notice.controller;

import com.seohamin.hisnetmobile.domain.notice.dto.NoticeListResponseDto;
import com.seohamin.hisnetmobile.domain.notice.dto.NoticeResponseDto;
import com.seohamin.hisnetmobile.domain.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService noticeService;

    // 일반공지 리스트 조회 API
    @GetMapping("/general")
    public ResponseEntity<NoticeListResponseDto> getGeneralNoticeList(
            @AuthenticationPrincipal final UserDetails userDetails
    ) {

        return ResponseEntity.ok(noticeService.getGeneralNoticeList(userDetails));
    }

    // 특정 일반공지 조회 API
    @GetMapping("/general/{id}")
    public ResponseEntity<NoticeResponseDto> getGeneralNotice(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final String id
    ) {

        return ResponseEntity.ok(noticeService.getGeneralNotice(userDetails, id));
    }

    // 학부 공지 리스트 조회 API
    @GetMapping("/department")
    public ResponseEntity<NoticeListResponseDto> getDepartmentNoticeList(
            @AuthenticationPrincipal final UserDetails userDetails,
            @RequestParam final String dept
    ) {

        return ResponseEntity.ok(noticeService.getDepartmentNoticeList(userDetails, dept));
    }

    // 특정 학부 공지 조회 API
    @GetMapping("/department/{id}")
    public ResponseEntity<NoticeResponseDto> getDepartmentNotice(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final String id,
            @RequestParam final String dept
    ) {

        return ResponseEntity.ok(noticeService.getDepartmentNotice(userDetails, id, dept));
    }

}
