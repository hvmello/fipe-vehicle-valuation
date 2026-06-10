package com.fipe.valuation.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the {@link WebClient} used to talk to the FIPE API.
 *
 * <p>Applies connect/response timeouts, a JSON {@code Accept} header, and the optional
 * {@code X-Subscription-Token} (only when configured). The base URL comes from {@link FipeProperties}.
 */
@Configuration
public class WebClientConfig {

    @Bean
    WebClient fipeWebClient(FipeProperties properties, WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.connectTimeout().toMillis())
                .responseTimeout(properties.responseTimeout());

        builder.baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (properties.hasToken()) {
            builder.defaultHeader("X-Subscription-Token", properties.subscriptionToken());
        }
        return builder.build();
    }
}
