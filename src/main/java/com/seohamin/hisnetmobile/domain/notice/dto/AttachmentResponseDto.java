package com.seohamin.hisnetmobile.domain.notice.dto;

/**
 * 공지 첨부파일 한 건.
 * @param index 원본 down.php 의 fidx (1부터). 다운로드 API 경로에 그대로 쓴다.
 * @param name  파일명 (원본 링크 텍스트에서 " (12,345 bytes)" 꼬리를 뗀 값)
 */
public record AttachmentResponseDto(
        int index,
        String name
) { }
