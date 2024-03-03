package com.productservice.service;

import com.productservice.entity.Product;
import com.productservice.model.ProductRequest;
import com.productservice.model.ProductResponse;
import com.productservice.repository.ProductRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.beans.BeanUtils.copyProperties;

@Service
@Log4j2
public class ProductServiceImpl implements ProductService{

    @Autowired
    private ProductRepository productRepository;

    @Override
    public long addProduct(ProductRequest productRequest) {
        log.info("Adding Product");

        Product product = Product.builder()
                .productName(productRequest.getName())
                .price(productRequest.getPrice())
                .quantity(productRequest.getQuantity())
                .build();
        productRepository.save(product);
        log.info("Product Created");
        return product.getProductId();
    }

    @Override
    public List<Product> getProducts() {
        log.info("Get Products");
        List<Product> products = productRepository.findAll();
        return products;
    }

    @Override
    public ProductResponse getProductById(long productId) {
        log.info("Get Product for productId: {}",productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(
                        () -> new RuntimeException("Product with given id is not found")
                );

        ProductResponse productResponse = new ProductResponse();
        copyProperties(product,productResponse);

        return productResponse;
    }


}
