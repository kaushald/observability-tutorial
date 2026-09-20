package com.kaushaldalvi.o11y.orders.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class DownstreamClientConfig {

    @Bean
    public RestClient kitchenRestClient(
            @Value("${kitchen.url}") String kitchenUrl,
            @Value("${downstream.read-timeout-ms}") long readTimeoutMs) {
        return buildClient(kitchenUrl, readTimeoutMs);
    }

    @Bean
    public RestClient deliveryRestClient(
            @Value("${delivery.url}") String deliveryUrl,
            @Value("${downstream.read-timeout-ms}") long readTimeoutMs) {
        return buildClient(deliveryUrl, readTimeoutMs);
    }

    private RestClient buildClient(String baseUrl, long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
