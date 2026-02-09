package com.apigateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthFilter extends AbstractGatewayFilterFactory<AdminAuthFilter.Config> {

    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    @Value("${app.admin.token:}")
    private String adminToken;

    public AdminAuthFilter() {
        super(Config.class);
    }

    @Override
    public String name() {
        return "AdminAuth";
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String providedToken = exchange.getRequest().getHeaders().getFirst(ADMIN_TOKEN_HEADER);

            if (adminToken.isBlank()) {
                return chain.filter(exchange);
            }

            if (providedToken == null || !providedToken.equals(adminToken)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        };
    }

    public static class Config {
    }
}
