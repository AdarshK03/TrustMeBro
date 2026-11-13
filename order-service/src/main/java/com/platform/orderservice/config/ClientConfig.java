package com.platform.orderservice.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Configuration
public class ClientConfig {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Bean
    public ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            RequestAttributes attribs = RequestContextHolder.getRequestAttributes();
            if (attribs instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest currentReq = servletAttributes.getRequest();
                String correlationId = currentReq.getHeader(CORRELATION_ID_HEADER);
                if (correlationId != null && !correlationId.isBlank()) {
                    request.getHeaders().set(CORRELATION_ID_HEADER, correlationId);
                }
            }
            return execution.execute(request, body);
        };
    }

    @Bean
    public RestClient.Builder restClientBuilder(ClientHttpRequestInterceptor correlationIdInterceptor) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(3000));
        factory.setReadTimeout(Duration.ofMillis(5000));

        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(correlationIdInterceptor);
    }
}

