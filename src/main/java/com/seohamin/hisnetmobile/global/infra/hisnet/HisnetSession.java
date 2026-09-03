package com.seohamin.hisnetmobile.global.infra.hisnet;

/**
 * 원본(HISNet) 서버에 대신 요청할 때 실어 보낼 세션 쿠키 묶음.
 * @param phpSessionId 원본 서버 세션 쿠키(PHPSESSID) 값
 * @param cookieId 원본 서버 보조 쿠키(cookie_id) 값, 없으면 null
 */
public record HisnetSession(
        String phpSessionId,
        String cookieId
) {

    /**
     * PHPSESSID 와 (있으면) cookie_id 를 Cookie 헤더 값으로 조립하는 메서드.
     * @return Cookie 헤더에 그대로 넣을 문자열
     */
    public String toCookieHeader() {
        final StringBuilder cookie = new StringBuilder("PHPSESSID=").append(phpSessionId);
        if (cookieId != null && !cookieId.isBlank()) {
            cookie.append("; cookie_id=").append(cookieId);
        }

        return cookie.toString();
    }
}