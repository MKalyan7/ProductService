package com.productservice.service;

import com.productservice.dto.request.CreateCategoryRequest;
import com.productservice.dto.request.UpdateCategoryRequest;
import com.productservice.dto.response.CategoryResponse;
import com.productservice.entity.Category;
import com.productservice.exception.ConflictException;
import com.productservice.exception.ResourceNotFoundException;
import com.productservice.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category testCategory;
    private CreateCategoryRequest createRequest;
    private UpdateCategoryRequest updateRequest;

    @BeforeEach
    void setUp() {
        String categoryId = UUID.randomUUID().toString();

        testCategory = Category.builder()
                .id("mongo-id-123")
                .categoryId(categoryId)
                .name("Electronics")
                .description("Electronic devices and gadgets")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        createRequest = CreateCategoryRequest.builder()
                .name("Electronics")
                .description("Electronic devices and gadgets")
                .build();

        updateRequest = UpdateCategoryRequest.builder()
                .name("Updated Electronics")
                .description("Updated description")
                .build();
    }

    @Test
    @DisplayName("Should create category successfully")
    void createCategory_Success() {
        when(categoryRepository.existsByName(createRequest.getName())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        CategoryResponse response = categoryService.createCategory(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo(testCategory.getName());
        assertThat(response.getDescription()).isEqualTo(testCategory.getDescription());

        verify(categoryRepository).existsByName(createRequest.getName());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when category name already exists")
    void createCategory_DuplicateName_ThrowsConflictException() {
        when(categoryRepository.existsByName(createRequest.getName())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(createRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(categoryRepository).existsByName(createRequest.getName());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Should update category successfully")
    void updateCategory_Success() {
        when(categoryRepository.findByCategoryId(testCategory.getCategoryId())).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByName(updateRequest.getName())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        CategoryResponse response = categoryService.updateCategory(testCategory.getCategoryId(), updateRequest);

        assertThat(response).isNotNull();
        verify(categoryRepository).findByCategoryId(testCategory.getCategoryId());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when updating non-existent category")
    void updateCategory_NotFound_ThrowsResourceNotFoundException() {
        String nonExistentId = "non-existent-id";
        when(categoryRepository.findByCategoryId(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(nonExistentId, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(categoryRepository).findByCategoryId(nonExistentId);
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Should get category by ID successfully")
    void getCategoryById_Success() {
        when(categoryRepository.findByCategoryId(testCategory.getCategoryId())).thenReturn(Optional.of(testCategory));

        CategoryResponse response = categoryService.getCategoryById(testCategory.getCategoryId());

        assertThat(response).isNotNull();
        assertThat(response.getCategoryId()).isEqualTo(testCategory.getCategoryId());
        assertThat(response.getName()).isEqualTo(testCategory.getName());

        verify(categoryRepository).findByCategoryId(testCategory.getCategoryId());
    }

    @Test
    @DisplayName("Should list all categories")
    void listCategories_Success() {
        Category category2 = Category.builder()
                .id("mongo-id-456")
                .categoryId(UUID.randomUUID().toString())
                .name("Clothing")
                .description("Apparel and fashion")
                .build();

        when(categoryRepository.findAll()).thenReturn(Arrays.asList(testCategory, category2));

        List<CategoryResponse> response = categoryService.listCategories();

        assertThat(response).hasSize(2);
        verify(categoryRepository).findAll();
    }
}
