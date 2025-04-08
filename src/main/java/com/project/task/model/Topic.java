package com.project.task.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Topic {
    private String name;
    private List<Vote> votes = new ArrayList<>();
}