package com.productservice.service;

import com.productservice.entity.Product;
import com.productservice.model.ProductRequest;
import com.productservice.model.ProductResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ProductService {
    long addProduct(ProductRequest productRequest);
    List<Product> getProducts();

    ProductResponse getProductById(long productId);
}
