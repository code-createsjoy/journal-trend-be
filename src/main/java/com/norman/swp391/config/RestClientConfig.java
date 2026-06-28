package com.norman.swp391.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient beans.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient externalApiRestClient(AppProperties appProperties) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                appProperties.getSync().getHttpConnectTimeoutMs());

        factory.setReadTimeout(
                appProperties.getSync().getHttpReadTimeoutMs());

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Bean("geminiRestClient")
    RestClient geminiRestClient(AppProperties appProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(appProperties.getGemini().getReadTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }

//    @Bean
//    RestClient externalApiRestClient(AppProperties appProperties, ObjectMapper objectMapper) {
//        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//        factory.setConnectTimeout(appProperties.getSync().getHttpConnectTimeoutMs());
//        factory.setReadTimeout(appProperties.getSync().getHttpReadTimeoutMs());
//
//        MappingJackson2HttpMessageConverter jackson2Converter = new MappingJackson2HttpMessageConverter(objectMapper);
//
//        return RestClient.builder()
//                .requestFactory(factory)
//                .messageConverters(converters -> {
//                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
//                    converters.add(0, jackson2Converter);
//                })
//                .build();
//    }
}


