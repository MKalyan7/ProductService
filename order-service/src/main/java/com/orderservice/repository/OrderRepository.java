package com.orderservice.repository;

import com.orderservice.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByOrderId(String orderId);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);
}
