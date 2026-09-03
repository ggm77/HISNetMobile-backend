package com.seohamin.hisnetmobile.global.security;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetLoginClient;
import com.seohamin.hisnetmobile.global.infra.hisnet.HisnetSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * 로컬 사용자 DB 없이, 원본(HISNet) 로그인 중계 결과로 인증을 판정하는 Provider.
 * <p>
 * 성공 시 원본 세션(PHPSESSID/cookie_id)을 담은 {@link HisnetUserDetails} 를 principal 로 하는
 * 인증 토큰을 반환한다. 비밀번호는 토큰에 남기지 않는다({@code credentials = null}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HisnetAuthenticationProvider implements AuthenticationProvider {

    private final HisnetLoginClient hisnetLoginClient;

    @Override
    public Authentication authenticate(final Authentication authentication) throws AuthenticationException {

        // 1) 입력값 추출 및 공백 검사
        final String username = String.valueOf(authentication.getPrincipal()).trim();
        final String rawPassword = authentication.getCredentials() == null
                ? null
                : String.valueOf(authentication.getCredentials());

        if (username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BadCredentialsException("아이디 또는 비밀번호가 비어있습니다.");
        }

        try {
            // 2) 원본 로그인 중계 → 세션 쿠키 확보
            final HisnetSession session = hisnetLoginClient.login(username, rawPassword);

            // 3) 원본 세션을 담은 principal 로 인증 토큰 발급 (비밀번호는 싣지 않음)
            final HisnetUserDetails principal = new HisnetUserDetails(
                    username,
                    session.phpSessionId(),
                    session.cookieId()
            );

            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        } catch (final CustomException ex) {
            if (ex.getExceptionCode() == ExceptionCode.LOGIN_FAILED) {
                throw new BadCredentialsException("로그인에 실패했습니다.");
            }

            log.error("[HISNet 로그인 중계 오류] code={}", ex.getExceptionCode().name());
            throw new AuthenticationServiceException("로그인 처리 중 오류가 발생했습니다.", ex);
        }
    }

    @Override
    public boolean supports(final Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}