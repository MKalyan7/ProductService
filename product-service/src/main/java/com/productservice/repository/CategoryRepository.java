package com.productservice.repository;

import com.productservice.entity.Category;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findByCategoryId(String categoryId);

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    boolean existsByCategoryId(String categoryId);
}
