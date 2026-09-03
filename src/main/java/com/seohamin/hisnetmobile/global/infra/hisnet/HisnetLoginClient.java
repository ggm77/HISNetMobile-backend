package com.seohamin.hisnetmobile.global.infra.hisnet;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.HttpCookie;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 원본(HISNet) 로그인을 대신 수행하는 클라이언트.
 * <p>
 * 로그인은 2단계다:
 * <ol>
 *   <li>{@code /login/login.php} GET → PHPSESSID/cookie_id 발급 + 매 요청 랜덤화되는 필드명({@code id_XX}/{@code password_XX}) 파싱</li>
 *   <li>같은 세션 쿠키로 {@code /login/_login.php} 평문 POST</li>
 * </ol>
 * 원본이 상태코드로 성패를 알려주지 않으므로, 발급된 세션으로 인증 필요 페이지를 다시 조회해 본문 기반으로 최종 판별한다.
 * <p>
 * <b>자격증명은 이 메서드가 실행되는 동안에만 메모리에 존재하며, 어떤 경우에도 로그/DB 로 남기지 않는다.</b>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HisnetLoginClient {

    private static final Charset HISNET_CHARSET = Charset.forName("EUC-KR");

    private static final String LOGIN_PAGE_PATH = "/login/login.php";
    private static final String LOGIN_SUBMIT_PATH = "/login/_login.php";

    // 매 요청 숫자 접미사가 바뀌는 동적 필드명
    private static final Pattern ID_FIELD_PATTERN = Pattern.compile("^id_\\d+$");
    private static final Pattern PASSWORD_FIELD_PATTERN = Pattern.compile("^password_\\d+$");

    // login.php 폼이 함께 전송하는 것으로 확인된 고정 필드 (값이 비어 있어도 함께 보낸다)
    private static final Map<String, String> DEFAULT_FORM_FIELDS = Map.of(
            "Language", "Korean",
            "part", "",
            "f_name", "",
            "agree", "",
            "saveid", ""
    );

    private final RestClient hisnetRestClient;
    private final HisnetClient hisnetClient;

    /**
     * 아이디/비밀번호로 원본 로그인을 수행하고 발급된 세션 쿠키를 반환하는 메서드.
     * @param username 원본 로그인 아이디 (학번)
     * @param rawPassword 평문 비밀번호 — 반환 후 호출측에서 즉시 폐기해야 한다
     * @return 인증된 원본 세션 쿠키
     */
    public HisnetSession login(
            final String username,
            final String rawPassword
    ) {
        // 1) 로그인 페이지 GET → 세션 쿠키 + 동적 필드명 확보
        final LoginPage loginPage = fetchLoginPage();

        // 2) 같은 세션으로 _login.php 평문 POST
        final HisnetSession session = submitCredentials(loginPage, username, rawPassword);

        // 3) 발급된 세션이 실제 인증 상태인지 본문 기반으로 검증
        try {
            hisnetClient.verifySession(session);
        } catch (final CustomException ex) {
            if (ex.getExceptionCode() == ExceptionCode.SESSION_EXPIRED) {
                throw new CustomException(ExceptionCode.LOGIN_FAILED);
            }
            throw ex;
        }

        return session;
    }

    /**
     * 로그인 페이지를 GET 해 세션 쿠키와 동적 필드명, 함께 보낼 히든 필드를 수집하는 메서드.
     */
    private LoginPage fetchLoginPage() {
        try {
            return hisnetRestClient.get()
                    .uri(LOGIN_PAGE_PATH)
                    .exchange((clientRequest, clientResponse) -> {
                        final HttpStatusCode status = clientResponse.getStatusCode();
                        if (!status.is2xxSuccessful()) {
                            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED);
                        }

                        final SessionCookies cookies = readSessionCookies(clientResponse.getHeaders(), null);
                        if (cookies.phpSessionId() == null) {
                            log.error("[HISNet 로그인 페이지] PHPSESSID 를 발급받지 못함");
                            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED);
                        }

                        final byte[] body = clientResponse.bodyTo(byte[].class);
                        final Document document = Jsoup.parse(
                                new String(body != null ? body : new byte[0], HISNET_CHARSET), "");

                        final String idField = findDynamicFieldName(document, ID_FIELD_PATTERN);
                        final String passwordField = findDynamicFieldName(document, PASSWORD_FIELD_PATTERN);
                        if (idField == null || passwordField == null) {
                            log.error("[HISNet 로그인 폼 파싱 실패] 동적 필드명(id_XX/password_XX)을 찾지 못함");
                            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED);
                        }

                        return new LoginPage(
                                cookies.phpSessionId(),
                                cookies.cookieId(),
                                idField,
                                passwordField,
                                extractFormFields(document, idField, passwordField)
                        );
                    });
        } catch (final CustomException ex) {
            throw ex;
        } catch (final Exception ex) {
            log.error("[HISNet 로그인 페이지 요청 실패]", ex);
            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED, ex);
        }
    }

    /**
     * 동적 필드명에 자격증명을 실어 _login.php 로 평문 POST 하는 메서드.
     * 성패는 여기서 확정하지 않고 회전됐을 수 있는 세션 쿠키만 확보한다.
     */
    private HisnetSession submitCredentials(
            final LoginPage loginPage,
            final String username,
            final String rawPassword
    ) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        // 폼에서 읽어둔 필드부터 복원
        loginPage.formFields().forEach(form::add);
        // 확인된 고정 필드는 없으면 채운다
        DEFAULT_FORM_FIELDS.forEach((name, value) -> form.putIfAbsent(name, List.of(value)));
        // 동적 필드명으로 자격증명 주입 (평문 — 클라이언트단 암호화 없음)
        form.set(loginPage.idField(), username);
        form.set(loginPage.passwordField(), rawPassword);

        try {
            return hisnetRestClient.post()
                    .uri(LOGIN_SUBMIT_PATH)
                    .header(HttpHeaders.COOKIE,
                            new HisnetSession(loginPage.phpSessionId(), loginPage.cookieId()).toCookieHeader())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((clientRequest, clientResponse) -> {
                        final HttpStatusCode status = clientResponse.getStatusCode();
                        // 성공 시 보통 302(main.php), 실패 시 200(폼 재렌더/alert). 3단계에서 최종 판별한다.
                        if (!status.is2xxSuccessful() && !status.is3xxRedirection()) {
                            log.warn("[HISNet 로그인 POST 비정상 응답] status={}", status.value());
                            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED);
                        }

                        // 세션이 회전될 수 있으니 응답 Set-Cookie 를 우선 사용, 없으면 기존 값 유지
                        final SessionCookies cookies = readSessionCookies(
                                clientResponse.getHeaders(),
                                new SessionCookies(loginPage.phpSessionId(), loginPage.cookieId()));

                        return new HisnetSession(cookies.phpSessionId(), cookies.cookieId());
                    });
        } catch (final CustomException ex) {
            throw ex;
        } catch (final Exception ex) {
            // username/password 는 절대 로깅하지 않는다
            log.error("[HISNet 로그인 POST 실패]", ex);
            throw new CustomException(ExceptionCode.HISNET_REQUEST_FAILED, ex);
        }
    }

    /**
     * 응답 헤더의 Set-Cookie 에서 PHPSESSID / cookie_id 를 뽑는 메서드.
     * 새 값이 없으면 fallback 값을 유지한다.
     */
    private SessionCookies readSessionCookies(
            final HttpHeaders headers,
            final SessionCookies fallback
    ) {
        String phpSessionId = fallback != null ? fallback.phpSessionId() : null;
        String cookieId = fallback != null ? fallback.cookieId() : null;

        for (final String setCookie : headers.getOrEmpty(HttpHeaders.SET_COOKIE)) {
            for (final HttpCookie cookie : HttpCookie.parse(setCookie)) {
                if (cookie.getValue() == null || cookie.getValue().isBlank()) {
                    continue;
                }
                if (cookie.getName().equalsIgnoreCase("PHPSESSID")) {
                    phpSessionId = cookie.getValue();
                } else if (cookie.getName().equalsIgnoreCase("cookie_id")) {
                    cookieId = cookie.getValue();
                }
            }
        }

        return new SessionCookies(phpSessionId, cookieId);
    }

    /**
     * 정규식에 맞는 첫 input 의 name 속성을 반환하는 메서드.
     */
    private String findDynamicFieldName(
            final Document document,
            final Pattern pattern
    ) {
        for (final Element input : document.select("input[name]")) {
            final String name = input.attr("name");
            if (pattern.matcher(name).matches()) {
                return name;
            }
        }

        return null;
    }

    /**
     * 로그인 폼이 함께 전송하는 필드(히든/선택 값)를 그대로 수집하는 메서드.
     * 동적 아이디/비밀번호 필드와 버튼류는 제외한다.
     */
    private Map<String, String> extractFormFields(
            final Document document,
            final String idField,
            final String passwordField
    ) {
        final Map<String, String> fields = new LinkedHashMap<>();

        Element form = document.selectFirst("form[name=login]");
        if (form == null) {
            form = document.selectFirst("form");
        }
        if (form == null) {
            return fields;
        }

        for (final Element input : form.select("input[name]")) {
            final String name = input.attr("name");
            final String type = input.attr("type").toLowerCase(Locale.ROOT);

            if (name.equals(idField) || name.equals(passwordField)) {
                continue;
            }
            if (type.equals("submit") || type.equals("button") || type.equals("image") || type.equals("reset")) {
                continue;
            }
            if ((type.equals("checkbox") || type.equals("radio")) && !input.hasAttr("checked")) {
                continue;
            }

            fields.put(name, input.attr("value"));
        }

        for (final Element select : form.select("select[name]")) {
            final Element selected = select.selectFirst("option[selected]");
            fields.put(select.attr("name"), selected != null ? selected.attr("value") : "");
        }

        return fields;
    }

    /**
     * 로그인 페이지 GET 결과 — 세션 쿠키 + 동적 필드명 + 함께 보낼 폼 필드.
     */
    private record LoginPage(
            String phpSessionId,
            String cookieId,
            String idField,
            String passwordField,
            Map<String, String> formFields
    ) {
    }

    /**
     * Set-Cookie 에서 추려낸 원본 세션 쿠키 한 쌍.
     */
    private record SessionCookies(
            String phpSessionId,
            String cookieId
    ) {
    }
}