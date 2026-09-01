package com.seohamin.hisnetmobile.global.infra.hisnet;

/**
 * 원본(HISNet) 서버에 대신 요청할 때 실어 보낼 세션 쿠키 묶음.
 * @param phpSessionId 원본 서버 세션 쿠키(PHPSESSID) 값
 * @param cookieId 원본 서버 보조 쿠키(cookie_id) 값, 없으면 null
 */
public record HisnetSession(
        String phpSessionId,
        String cookieId
) { }