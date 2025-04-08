package com.project.task.model;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Vote {
    private String name;
    private String description;
    private List<String> options;
    private Map<String, Integer> results = new HashMap<>(); // option -> count
    private String createdBy;
}
