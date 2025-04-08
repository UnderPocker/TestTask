package com.project.task.model;

import lombok.Data;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class VotingData {
    private Map<String, Topic> topics = new HashMap<>();
    private Set<String> users = new HashSet<>();
}
