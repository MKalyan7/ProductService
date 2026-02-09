package com.orderservice.repository;

import com.orderservice.entity.SeedRun;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeedRunRepository extends MongoRepository<SeedRun, String> {

    Optional<SeedRun> findTopByOrderByStartedAtDesc();
}
