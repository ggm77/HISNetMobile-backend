package com.seohamin.hisnetmobile.domain.notice.controller;

import com.seohamin.hisnetmobile.domain.notice.constant.NoticeBoard;
import com.seohamin.hisnetmobile.domain.notice.dto.NoticeListResponseDto;
import com.seohamin.hisnetmobile.domain.notice.dto.NoticeResponseDto;
import com.seohamin.hisnetmobile.domain.notice.service.NoticeAttachmentService;
import com.seohamin.hisnetmobile.domain.notice.service.NoticeService;
import jakarta.servlet.http.HttpServletResponse;
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
    private final NoticeAttachmentService noticeAttachmentService;

    // 고정 게시판(general / scholarship / dormitory) 리스트 조회 API (page: 1부터, 기본 1)
    @GetMapping("/{board}")
    public ResponseEntity<NoticeListResponseDto> getNoticeList(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final String board,
            @RequestParam(defaultValue = "1") final int page
    ) {

        return ResponseEntity.ok(
                noticeService.getFixedBoardNoticeList(userDetails, NoticeBoard.from(board), page));
    }

    // 고정 게시판 특정 공지 조회 API
    @GetMapping("/{board}/{id}")
    public ResponseEntity<NoticeResponseDto> getNotice(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final String board,
            @PathVariable final String id
    ) {

        return ResponseEntity.ok(
                noticeService.getFixedBoardNotice(userDetails, NoticeBoard.from(board), id));
    }

    // 고정 게시판 첨부파일 다운로드 API (index: files[].index, name: 선택 — files[].name 을 넘기면 파일명으로 사용)
    @GetMapping("/{board}/{id}/attachments/{index}")
    public void downloadAttachment(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final String board,
            @PathVariable final String id,
            @PathVariable final int index,
            @RequestParam(required = false) final String name,
            final HttpServletResponse response
    ) {

        noticeAttachmentService.downloadFixedBoardAttachment(
                userDetails, NoticeBoard.from(board), id, index, name, response);
    }

    // 학부 공지 리스트 조회 API (page: 1부터, 기본 1)
    @GetMapping("/department")
    public ResponseEntity<NoticeListResponseDto> getDepartmentNoticeList(
            @AuthenticationPrincipal final UserDetails userDetails,
            @RequestParam final String dept,
            @RequestParam(defaultValue = "1") final int page
    ) {

        return ResponseEntity.ok(noticeService.getDepartmentNoticeList(userDetails, dept, page));
    }

    // 특정 학부 공지 조회 API
    @GetMapping("/department/{id}")
    public ResponseEntity<NoticeResponseDto> getDepartmentNotice(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final String id,
            @RequestParam final String dept
    ) {

        return ResponseEntity.ok(noticeService.getDepartmentNotice(userDetails, dept, id));
    }

    // 학부 공지 첨부파일 다운로드 API
    @GetMapping("/department/{id}/attachments/{index}")
    public void downloadDepartmentAttachment(
            @AuthenticationPrincipal final UserDetails userDetails,
            @PathVariable final String id,
            @PathVariable final int index,
            @RequestParam final String dept,
            @RequestParam(required = false) final String name,
            final HttpServletResponse response
    ) {

        noticeAttachmentService.downloadDepartmentAttachment(userDetails, dept, id, index, name, response);
    }

}
