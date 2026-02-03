package com.productservice.service;

import com.productservice.dto.request.CreateCategoryRequest;
import com.productservice.dto.request.UpdateCategoryRequest;
import com.productservice.dto.response.CategoryResponse;
import com.productservice.entity.Category;
import com.productservice.exception.ConflictException;
import com.productservice.exception.ErrorCode;
import com.productservice.exception.ResourceNotFoundException;
import com.productservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Creating category with name: {}", request.getName());

        if (categoryRepository.existsByName(request.getName())) {
            throw new ConflictException(ErrorCode.CATEGORY_NAME_CONFLICT,
                    "Category with name '" + request.getName() + "' already exists");
        }

        String categoryId = UUID.randomUUID().toString();

        Category category = Category.builder()
                .categoryId(categoryId)
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Category savedCategory = categoryRepository.save(category);

        log.info("Created category with ID: {}", categoryId);
        return mapToResponse(savedCategory);
    }

    @Transactional
    public CategoryResponse updateCategory(String categoryId, UpdateCategoryRequest request) {
        log.info("Updating category with ID: {}", categoryId);

        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND,
                        "Category not found with ID: " + categoryId));

        if (request.getName() != null && !request.getName().equals(category.getName())) {
            if (categoryRepository.existsByName(request.getName())) {
                throw new ConflictException(ErrorCode.CATEGORY_NAME_CONFLICT,
                        "Category with name '" + request.getName() + "' already exists");
            }
            category.setName(request.getName());
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }

        Category updatedCategory = categoryRepository.save(category);
        log.info("Updated category with ID: {}", categoryId);
        return mapToResponse(updatedCategory);
    }

    public CategoryResponse getCategoryById(String categoryId) {
        log.info("Fetching category with ID: {}", categoryId);

        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND,
                        "Category not found with ID: " + categoryId));

        return mapToResponse(category);
    }

    public List<CategoryResponse> listCategories() {
        log.info("Listing all categories");

        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .description(category.getDescription())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
