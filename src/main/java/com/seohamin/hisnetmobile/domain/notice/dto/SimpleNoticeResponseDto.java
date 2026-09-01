package com.seohamin.hisnetmobile.domain.notice.dto;

import java.time.LocalDate;

public record SimpleNoticeResponseDto(
        String no,
        String subject,
        Integer files,
        String writer,
        LocalDate time,
        Integer read
) {
}
