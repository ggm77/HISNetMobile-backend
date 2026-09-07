package com.seohamin.hisnetmobile.domain.notice.dto;

import java.time.LocalDate;
import java.util.List;

public record NoticeResponseDto(
        // read.php 가 요구하는 글 ID (요청에 쓴 값을 그대로 실어준다)
        String id,
        String subject,
        List<AttachmentResponseDto> files,
        String writer,
        LocalDate time,
        Integer read,
        String category,
        // 본문 평문. 이미지로만 이뤄진 공지는 빈 문자열일 수 있다 → images 를 함께 본다.
        String body,
        // 본문에 삽입된 이미지의 절대 URL (원본 HISNet 호스팅, 대부분 세션 없이 접근 가능). 없으면 빈 리스트.
        List<String> images
) { }
