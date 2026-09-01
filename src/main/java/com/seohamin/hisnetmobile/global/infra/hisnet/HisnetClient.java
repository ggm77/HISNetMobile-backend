package com.seohamin.hisnetmobile.global.infra.hisnet;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;

import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.time.Duration;

/**
 * 원본(HISNet) 서버로 조회 요청을 대신 보내는 HTTP 클라이언트.
 * <p>
 * - 매핑된 PHPSESSID/cookie_id 를 쿠키로 실어 GET 릴레이
 * - 원본이 전 페이지 EUC-KR 이므로 바이트로 받아 명시적으로 디코딩
 * - 세션 만료 시 원본이 HTTP 200 + 소형 JS alert 스텁을 주므로 본문 기반으로 만료를 판별해 예외로 전환
 */
@Component
@Slf4j
public class HisnetClient {

    // 원본 EUC-KR 인코딩
    private static final Charset HISNET_CHARSET = Charset.forName("EUC-KR");

    // 세션 만료 시 내려오는 스텁 응답은 ~1.8KB 수준, 정상 조회 페이지는 100KB 이상
    private static final int LOGIN_STUB_MAX_BYTES = 8 * 1024;

    // 미인증/만료 스텁에 포함되는 안내 문구
    private static final String LOGIN_REQUIRED_MARKER = "로그인 후 이용";

    @Value("${hisnet.base-url}")
    private String baseUrl;

    @Value("${hisnet.connect-timeout-second:5}")
    private int connectTimeoutSecond;

    @Value("${hisnet.read-timeout-second:10}")
    private int readTimeoutSecond;

    private RestClient restClient;

    @PostConstruct
    private void init() {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(buildRequestFactory())
                .build();
    }

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
        final Document document = Jsoup.parse(
                new String(body, HISNET_CHARSET),
                baseUrl
        );

        verifyAuthenticated(body, document);

        return document;
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
        try {
            final byte[] body = restClient.get()
                    .uri(path)
                    .header("Cookie", buildCookieHeader(session))
                    .retrieve()
                    .body(byte[].class);

            if (body == null || body.length == 0) {
                log.warn("[HISNet 빈 응답] path={}", path);
                throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED);
            }

            return body;
        } catch (final RestClientException ex) {
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

    /**
     * PHPSESSID 와 (있으면) cookie_id 를 Cookie 헤더 문자열로 조립하는 메서드.
     * @param session 세션 쿠키
     * @return Cookie 헤더 값
     */
    private String buildCookieHeader(final HisnetSession session) {
        if (session == null || session.phpSessionId() == null || session.phpSessionId().isBlank()) {
            throw new CustomException(ExceptionCode.SESSION_EXPIRED);
        }

        final StringBuilder cookie = new StringBuilder("PHPSESSID=").append(session.phpSessionId());
        if (session.cookieId() != null && !session.cookieId().isBlank()) {
            cookie.append("; cookie_id=").append(session.cookieId());
        }

        return cookie.toString();
    }

    private ClientHttpRequestFactory buildRequestFactory() {
        final HttpClient httpClient = HttpClient.newBuilder()
                // 원본 사이트의 HTTPS→HTTP 다운그레이드 리다이렉트를 따라가지 않도록 수동 처리
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSecond))
                .build();

        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSecond));

        return requestFactory;
    }
}