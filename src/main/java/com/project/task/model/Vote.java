package com.project.task.model;

import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Vote {
    private String name;
    private String description;
    private List<String> options;
    private Map<String, Integer> results = new ConcurrentHashMap<>(); // option -> count
    private String createdBy;
}
