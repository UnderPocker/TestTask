package com.project.task.model;

import lombok.Data;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Data
public class VotingData {
    private Map<String, Topic> topics = new ConcurrentHashMap<>();
    private Set<String> users = new CopyOnWriteArraySet<>();
}
