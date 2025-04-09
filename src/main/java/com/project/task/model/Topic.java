package com.project.task.model;

import lombok.Data;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Topic {
    private String name;
    private Map<String, Vote> votes = new ConcurrentHashMap<>();
}