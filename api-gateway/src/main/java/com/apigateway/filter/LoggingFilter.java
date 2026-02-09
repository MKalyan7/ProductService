package com.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        long startTime = System.currentTimeMillis();

        log.info("[{}] >>> {} {}", correlationId, method, path);

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int status = 0;
                    if (exchange.getResponse().getStatusCode() != null) {
                        status = exchange.getResponse().getStatusCode().value();
                    }
                    log.info("[{}] <<< {} {} -> {} ({}ms)", correlationId, method, path, status, duration);
                }));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
