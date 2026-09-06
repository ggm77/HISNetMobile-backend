package com.seohamin.hisnetmobile.domain.notice.dto;

import java.util.List;

/**
 * 공지 목록 응답.
 * <p>
 * 원본(list.php)은 {@code Page} 파라미터로 페이징하며 한 페이지에 일반 글 15개가 실린다.
 * 고정공지는 모든 페이지에 반복 노출되므로 2페이지부터는 제외해 내려준다.
 *
 * @param notices     현재 페이지의 공지 요약 (1페이지는 고정공지 포함)
 * @param page        현재 페이지 (1부터)
 * @param totalPages  원본 페이저에서 파악한 마지막 페이지
 * @param hasNext     다음 페이지 존재 여부
 * @param hasPrevious 이전 페이지 존재 여부
 */
public record NoticeListResponseDto(
        List<SimpleNoticeResponseDto> notices,
        int page,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) { }