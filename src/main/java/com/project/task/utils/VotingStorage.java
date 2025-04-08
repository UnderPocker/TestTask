package com.project.task.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.task.model.VotingData;
import lombok.Getter;
import java.io.File;
import java.io.IOException;

public class VotingStorage {
    private static final String FILE_NAME = "data.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    @Getter
    private static VotingData data = new VotingData();

    public static void save() throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_NAME), data);
    }

    public static void load() throws IOException {
        File file = new File(FILE_NAME);
        if (file.exists()) {
            data = mapper.readValue(file, VotingData.class);
        }
    }
}

