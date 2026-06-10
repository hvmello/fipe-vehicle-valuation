package com.fipe.valuation.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Adds baseline security response headers to every response (NFR-7). The API serves only JSON to a
 * browser SPA, so these defend the client against MIME sniffing, clickjacking and referrer leakage
 * without affecting the read-only data flow.
 *
 * <p>Headers are written in {@code beforeCommit} so they survive even when the response is produced
 * by a downstream component (e.g. the CORS or error handling layers). Runs early so the headers are
 * present on every path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
