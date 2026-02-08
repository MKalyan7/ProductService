package com.orderservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductServiceErrorResponse {

    private String timestamp;
    private String path;
    private String errorCode;
    private String message;
    private List<String> details;
}
