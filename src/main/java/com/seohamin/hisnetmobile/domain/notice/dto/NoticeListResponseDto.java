package com.seohamin.hisnetmobile.domain.notice.dto;

import java.util.List;

public record NoticeListResponseDto(
        List<SimpleNoticeResponseDto> notices
) { }
