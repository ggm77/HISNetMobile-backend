package com.seohamin.hisnetmobile.domain.notice.dto;

import java.time.LocalDate;
import java.util.List;

public record NoticeResponseDto(
        // read.php 가 요구하는 글 ID (요청에 쓴 값을 그대로 실어준다)
        String id,
        String subject,
        List<String> files,
        String writer,
        LocalDate time,
        Integer read,
        String category,
        String body
) { }
