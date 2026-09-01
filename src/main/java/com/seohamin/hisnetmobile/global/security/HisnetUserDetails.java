package com.seohamin.hisnetmobile.global.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 인증된 사용자에 매핑된 원본(HISNet) 세션 정보를 담는 UserDetails 구현체.
 * <p>
 * 설계 문서 기준으로 로그인 자격증명은 절대 저장하지 않고, 로그인 중계 과정에서 발급받은
 * PHPSESSID/cookie_id 만 프록시 세션에 매핑해 둔다. 조회 요청 시 이 값으로 원본 서버에 대신 요청한다.
 */
@Getter
@RequiredArgsConstructor
public class HisnetUserDetails implements UserDetails {

    /** 원본 사이트 로그인 아이디 (학번) */
    private final String username;

    /** 원본 서버가 발급한 세션 쿠키 값 */
    private final String phpSessionId;

    /** 원본 서버가 함께 세팅하는 보조 쿠키 값 (없을 수 있음) */
    private final String cookieId;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /**
     * 자격증명(비밀번호)은 서버에 남기지 않으므로 항상 null.
     */
    @Override
    public String getPassword() {
        return null;
    }
}