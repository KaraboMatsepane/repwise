package com.example.actvityservice.repository;

import com.example.actvityservice.dto.ActivityResponse;
import com.example.actvityservice.model.Activity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ActivityRepository extends MongoRepository<Activity, String> {
    List<Activity> findByUserId(String userId);
}
