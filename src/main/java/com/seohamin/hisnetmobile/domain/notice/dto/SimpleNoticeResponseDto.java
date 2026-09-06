package com.seohamin.hisnetmobile.domain.notice.dto;

import java.time.LocalDate;

public record SimpleNoticeResponseDto(
        // read.php 가 요구하는 글 ID (list.php 링크의 id 파라미터). 화면에 보이는 게시판 순번과 다르다.
        String id,
        String subject,
        Integer files,
        String writer,
        LocalDate time,
        Integer read,
        boolean pinned
) {
}