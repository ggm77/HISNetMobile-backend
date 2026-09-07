package com.seohamin.hisnetmobile.domain.notice.service;

import com.seohamin.hisnetmobile.domain.notice.constant.Board;
import com.seohamin.hisnetmobile.domain.notice.constant.NoticeBoard;
import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetClient;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetSession;
import com.seohamin.hisnetmobile.global.security.HisnetUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공지 첨부파일 다운로드를 원본(HISNet) {@code down.php} 에 중계하는 서비스.
 * <p>
 * down.php 는 {@code Board + id + fidx} 만으로 파일 바이트를 준다(세션이 없어도 되지만 있으면 함께 보냄).
 * 응답을 메모리에 담지 않고 서블릿 출력 스트림으로 그대로 흘려보낸다. 파일명은 요청에 함께 온 값을
 * 우선 쓰고, 없으면 원본 응답의 {@code Content-Disposition}(EUC-KR 생바이트)을 다시 디코딩해 복원한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoticeAttachmentService {

    private static final Pattern NOTICE_ID_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern CD_FILENAME_PATTERN =
            Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);
    private static final Charset HISNET_CHARSET = Charset.forName("EUC-KR");

    // 확장자 → Content-Type (없으면 octet-stream 으로 강제 다운로드)
    private static final Map<String, MediaType> CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", MediaType.APPLICATION_PDF),
            Map.entry("png", MediaType.IMAGE_PNG),
            Map.entry("jpg", MediaType.IMAGE_JPEG),
            Map.entry("jpeg", MediaType.IMAGE_JPEG),
            Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("txt", new MediaType("text", "plain", StandardCharsets.UTF_8)),
            Map.entry("csv", new MediaType("text", "csv", StandardCharsets.UTF_8)),
            Map.entry("zip", MediaType.parseMediaType("application/zip")),
            Map.entry("hwp", MediaType.parseMediaType("application/x-hwp")),
            Map.entry("hwpx", MediaType.parseMediaType("application/haansofthwpx")),
            Map.entry("doc", MediaType.parseMediaType("application/msword")),
            Map.entry("docx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", MediaType.parseMediaType("application/vnd.ms-excel")),
            Map.entry("xlsx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("ppt", MediaType.parseMediaType("application/vnd.ms-powerpoint")),
            Map.entry("pptx", MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
    );

    private final HisnetClient hisnetClient;

    /**
     * 고정 게시판(일반/장학/생활관) 첨부파일을 다운로드하는 메서드.
     */
    public void downloadFixedBoardAttachment(
            final UserDetails userDetails,
            final NoticeBoard board,
            final String noticeId,
            final int index,
            final String nameOverride,
            final HttpServletResponse response
    ) {
        relay(userDetails, board.code(), noticeId, index, nameOverride, response);
    }

    /**
     * 학부공지 첨부파일을 다운로드하는 메서드.
     */
    public void downloadDepartmentAttachment(
            final UserDetails userDetails,
            final String departmentId,
            final String noticeId,
            final int index,
            final String nameOverride,
            final HttpServletResponse response
    ) {
        relay(userDetails, Board.department(departmentId), noticeId, index, nameOverride, response);
    }

    /**
     * down.php 를 릴레이해 응답 스트림을 그대로 서블릿 응답에 흘려보내는 공통 메서드.
     */
    private void relay(
            final UserDetails userDetails,
            final String boardCode,
            final String noticeId,
            final int index,
            final String nameOverride,
            final HttpServletResponse response
    ) {
        if (noticeId == null || !NOTICE_ID_PATTERN.matcher(noticeId).matches()) {
            throw new CustomException(ExceptionCode.INVALID_NOTICE_ID);
        }
        if (index < 1) {
            throw new CustomException(ExceptionCode.INVALID_ATTACHMENT_INDEX);
        }

        final HisnetSession session = resolveSession(userDetails);
        final String path = "/myboard/down.php?Board=" + encode(boardCode)
                + "&id=" + encode(noticeId)
                + "&fidx=" + index;

        hisnetClient.download(path, session, (headers, body) -> {
            // 빈 본문 = 없는 fidx
            final byte[] head = body.readNBytes(1);
            if (head.length == 0) {
                throw new CustomException(ExceptionCode.ATTACHMENT_NOT_FOUND);
            }

            final String filename = resolveFilename(nameOverride, headers, index);

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(contentTypeFor(filename).toString());
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename));
            // 원본이 Content-Length 를 주면 그대로 전달, 없으면 청크 전송
            final long contentLength = headers.getContentLength();
            if (contentLength >= 0) {
                response.setContentLengthLong(contentLength);
            }

            final OutputStream out = response.getOutputStream();
            try {
                out.write(head);
                StreamUtils.copy(body, out);
                out.flush();
            } catch (final IOException streamEx) {
                // 전송 중 클라이언트가 끊는 경우 — 헤더는 이미 나갔으니 조용히 종료
                log.debug("[첨부 전송 중단] board={}, id={}, fidx={}", boardCode, noticeId, index, streamEx);
            }
        });
    }

    /**
     * 파일명 결정: 요청에 온 값 우선 → 원본 Content-Disposition(EUC-KR) 복원 → 기본값.
     */
    private String resolveFilename(
            final String nameOverride,
            final HttpHeaders headers,
            final int index
    ) {
        if (nameOverride != null && !nameOverride.isBlank()) {
            return sanitize(nameOverride);
        }

        final String rawCd = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (rawCd != null) {
            // HTTP 헤더는 ISO-8859-1 로 들어오므로 바이트를 되살려 EUC-KR 로 다시 읽는다
            final String decoded = new String(rawCd.getBytes(StandardCharsets.ISO_8859_1), HISNET_CHARSET);
            final Matcher matcher = CD_FILENAME_PATTERN.matcher(decoded);
            if (matcher.find()) {
                final String name = sanitize(matcher.group(1).trim());
                if (!name.isBlank()) {
                    return name;
                }
            }
        }

        return "attachment_" + index;
    }

    /**
     * 경로 구분자/제어문자를 제거해 헤더에 안전한 파일명으로 만드는 메서드.
     */
    private String sanitize(final String name) {
        return name.replaceAll("[\\\\/\\r\\n\\t\\p{Cntrl}]", "").trim();
    }

    /**
     * RFC 5987 {@code filename*} + ASCII 폴백을 함께 담은 Content-Disposition 값을 만드는 메서드.
     */
    private String contentDisposition(final String filename) {
        final String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "");
        final String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 확장자로 Content-Type 을 추정하는 메서드. 모르면 octet-stream.
     */
    private MediaType contentTypeFor(final String filename) {
        final int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            final String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
            final MediaType type = CONTENT_TYPES.get(ext);
            if (type != null) {
                return type;
            }
        }

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 인증 주체에서 원본 세션 쿠키를 꺼내는 메서드.
     * 로그인 중계로 채워진 HisnetUserDetails 가 아니면 세션 만료로 취급한다.
     */
    private HisnetSession resolveSession(final UserDetails userDetails) {
        if (!(userDetails instanceof HisnetUserDetails hisnetUserDetails)) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }

        return new HisnetSession(
                hisnetUserDetails.getPhpSessionId(),
                hisnetUserDetails.getCookieId()
        );
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
