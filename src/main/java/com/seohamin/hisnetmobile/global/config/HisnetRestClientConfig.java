package com.seohamin.hisnetmobile.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 원본(HISNet) 서버 릴레이 전용 RestClient 빈 설정.
 * <p>
 * 조회 릴레이(HisnetClient)와 로그인 중계(HisnetLoginClient)가 같은 설정을 공유한다.
 * - 리다이렉트는 수동 처리 (HTTPS→HTTP 다운그레이드/로그인 프레임 이동을 따라가지 않음)
 * - 쿠키는 클라이언트 코드에서 직접 관리 (쿠키 저장소를 붙이지 않음)
 */
@Configuration
public class HisnetRestClientConfig {

    @Bean
    public RestClient hisnetRestClient(
            @Value("${hisnet.base-url:https://hisnet.handong.edu}") final String baseUrl,
            @Value("${hisnet.connect-timeout-second:5}") final int connectTimeoutSecond,
            @Value("${hisnet.read-timeout-second:10}") final int readTimeoutSecond
    ) {
        final HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSecond))
                .build();

        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSecond));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "HisnetMobile/1.0 (+proxy)")
                .build();
    }
}