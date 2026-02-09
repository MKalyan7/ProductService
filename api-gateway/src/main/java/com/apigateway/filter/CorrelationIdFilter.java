package com.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        String finalCorrelationId = correlationId;

        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                getHeaders().putIfAbsent(CORRELATION_ID_HEADER, java.util.List.of(finalCorrelationId));
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer>> body) {
                getHeaders().putIfAbsent(CORRELATION_ID_HEADER, java.util.List.of(finalCorrelationId));
                return super.writeAndFlushWith(body);
            }
        };

        return chain.filter(exchange.mutate()
                .request(mutatedRequest)
                .response(decoratedResponse)
                .build());
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
