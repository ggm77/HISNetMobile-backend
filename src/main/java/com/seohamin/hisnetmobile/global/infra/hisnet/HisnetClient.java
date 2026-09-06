package com.seohamin.hisnetmobile.global.infra.hisnet;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * 원본(HISNet) 서버로 조회 요청을 대신 보내는 HTTP 클라이언트.
 * <p>
 * - 매핑된 PHPSESSID/cookie_id 를 쿠키로 실어 GET 릴레이
 * - 원본이 전 페이지 EUC-KR 이므로 바이트로 받아 명시적으로 디코딩
 * - 세션 만료 시 원본이 HTTP 200 + 소형 JS alert 스텁을 주므로 본문 기반으로 만료를 판별해 예외로 전환
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HisnetClient {

    // 원본 EUC-KR 인코딩
    private static final Charset HISNET_CHARSET = Charset.forName("EUC-KR");

    // 세션 만료 시 내려오는 스텁 응답은 ~1.8KB 수준, 정상 조회 페이지는 100KB 이상
    private static final int LOGIN_STUB_MAX_BYTES = 8 * 1024;

    // 미인증/만료 스텁에 포함되는 안내 문구
    private static final String LOGIN_REQUIRED_MARKER = "로그인 후 이용";

    // 세션 생존 확인용으로 때리는 가벼운 인증 필요 페이지 (일반공지 목록 1페이지)
    private static final String SESSION_PROBE_PATH =
            "/myboard/list.php?Board=NB0001&Page=1&FindIt=&FindText=";

    @Value("${hisnet.base-url:https://hisnet.handong.edu}")
    private String baseUrl;

    private final RestClient hisnetRestClient;

    /**
     * 원본 서버의 조회 페이지를 GET 으로 가져와 EUC-KR 로 디코딩한 Document 를 반환하는 메서드.
     * @param path 원본 기준 경로 + 쿼리스트링 (예: /myboard/list.php?Board=NB0001&Page=1)
     * @param session 원본 서버에 실어 보낼 세션 쿠키
     * @return 파싱된 Document
     */
    public Document get(
            final String path,
            final HisnetSession session
    ) {
        final byte[] body = request(path, session);
        final Document document = Jsoup.parse(new String(body, HISNET_CHARSET), baseUrl);

        verifyAuthenticated(body, document);

        return document;
    }

    /**
     * 매핑된 세션이 아직 원본에서 인증 상태인지 확인하는 메서드.
     * 스텁 응답이면 {@link ExceptionCode#SESSION_EXPIRED} 를 던진다.
     * @param session 확인할 세션 쿠키
     */
    public void verifySession(final HisnetSession session) {
        get(SESSION_PROBE_PATH, session);
    }

    /**
     * 바이너리 응답(첨부 다운로드 등)을 원본에서 GET 해 스트리밍으로 소비자에게 넘기는 메서드.
     * <p>
     * 응답 헤더와 본문 스트림을 콜백 안에서 소비하며(콜백 종료 시 커넥션이 닫힘), 본문을 메모리에
     * 통째로 담지 않는다. down.php 는 세션이 없어도 파일을 주지만, 있으면 쿠키를 함께 실어 보낸다.
     * @param path     원본 기준 경로 + 쿼리스트링 (예: /myboard/down.php?Board=..&id=..&fidx=1)
     * @param session  원본 세션 쿠키 (null 또는 비어 있으면 쿠키 없이 요청)
     * @param consumer (응답 헤더, 본문 스트림) 을 받아 처리하는 콜백
     */
    public void download(
            final String path,
            final HisnetSession session,
            final BinaryResponseConsumer consumer
    ) {
        try {
            hisnetRestClient.get()
                    .uri(path)
                    .headers(headers -> {
                        if (session != null && session.phpSessionId() != null && !session.phpSessionId().isBlank()) {
                            headers.add(HttpHeaders.COOKIE, session.toCookieHeader());
                        }
                    })
                    .exchange((clientRequest, clientResponse) -> {
                        final HttpStatusCode status = clientResponse.getStatusCode();
                        if (status.is3xxRedirection()) {
                            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
                        }
                        if (!status.is2xxSuccessful()) {
                            log.warn("[HISNet 다운로드 비정상 응답] path={}, status={}", path, status.value());
                            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED);
                        }

                        consumer.accept(clientResponse.getHeaders(), clientResponse.getBody());
                        return null;
                    });
        } catch (final CustomException ex) {
            throw ex;
        } catch (final Exception ex) {
            log.error("[HISNet 다운로드 실패] path={}", path, ex);
            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED, ex);
        }
    }

    /**
     * 원본 바이너리 응답의 헤더와 본문 스트림을 받아 처리하는 콜백.
     */
    @FunctionalInterface
    public interface BinaryResponseConsumer {
        void accept(HttpHeaders headers, InputStream body) throws IOException;
    }

    /**
     * 실제 GET 요청을 수행해 응답 바이트를 그대로 가져오는 메서드.
     * @param path 원본 기준 경로 + 쿼리스트링
     * @param session 세션 쿠키
     * @return 응답 본문 바이트 (EUC-KR)
     */
    private byte[] request(
            final String path,
            final HisnetSession session
    ) {
        if (session == null || session.phpSessionId() == null || session.phpSessionId().isBlank()) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }

        try {
            return hisnetRestClient.get()
                    .uri(path)
                    .header(HttpHeaders.COOKIE, session.toCookieHeader())
                    .exchange((clientRequest, clientResponse) -> {
                        final HttpStatusCode status = clientResponse.getStatusCode();

                        // 세션이 없으면 원본이 로그인 프레임으로 리다이렉트한다
                        if (status.is3xxRedirection()) {
                            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
                        }
                        if (!status.is2xxSuccessful()) {
                            log.warn("[HISNet 비정상 응답] path={}, status={}", path, status.value());
                            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED);
                        }

                        final byte[] body = clientResponse.bodyTo(byte[].class);
                        if (body == null || body.length == 0) {
                            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED);
                        }

                        return body;
                    });
        } catch (final CustomException ex) {
            throw ex;
        } catch (final Exception ex) {
            log.error("[HISNet 요청 실패] path={}", path, ex);
            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED, ex);
        }
    }

    /**
     * 원본 서버가 상태코드로는 만료를 알려주지 않으므로 본문으로 로그인 상태를 판별하는 메서드.
     * @param body 응답 본문 바이트
     * @param document 파싱된 Document
     */
    private void verifyAuthenticated(
            final byte[] body,
            final Document document
    ) {
        if (body.length < LOGIN_STUB_MAX_BYTES) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }

        if (document.text().contains(LOGIN_REQUIRED_MARKER)) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }
    }
}