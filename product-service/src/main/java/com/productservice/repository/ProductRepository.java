package com.productservice.repository;

import com.productservice.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findByProductId(String productId);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsByProductId(String productId);

    Page<Product> findByCategoryId(String categoryId, Pageable pageable);

    Page<Product> findByActive(boolean active, Pageable pageable);

    Page<Product> findByCategoryIdAndActive(String categoryId, boolean active, Pageable pageable);

    @Query("{ $and: [ " +
            "{ $or: [ { 'categoryId': ?0 }, { ?0: null } ] }, " +
            "{ $or: [ { 'active': ?1 }, { ?1: null } ] }, " +
            "{ $or: [ { 'price': { $gte: ?2 } }, { ?2: null } ] }, " +
            "{ $or: [ { 'price': { $lte: ?3 } }, { ?3: null } ] } " +
            "] }")
    Page<Product> findByFilters(String categoryId, Boolean active, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    @Query("{ $text: { $search: ?0 } }")
    Page<Product> searchByText(String searchText, Pageable pageable);
}
