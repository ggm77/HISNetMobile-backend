package com.seohamin.hisnetmobile.domain.notice.dto;

import java.time.LocalDate;
import java.util.List;

public record NoticeResponseDto(
        String no,
        String subject,
        List<String> files,
        String writer,
        LocalDate time,
        Integer read,
        String category,
        String body
) { }
