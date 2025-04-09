package com.project.task.server;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ClientSession {
    public enum State {
        DEFAULT, // обычное состояние — команды доступны
        LOGIN_WAITING_USERNAME,
        CREATING_VOTE_NAME,
        CREATING_VOTE_DESCRIPTION,
        CREATING_VOTE_OPTIONS_COUNT,
        CREATING_VOTE_OPTIONS,
        VOTING_CHOICE
    }

    private State state = State.DEFAULT;
    private String currentUsername;

    // переменные для создания голосования
    private String topicName;
    private String voteName;
    private String voteDescription;
    private int optionsCount;
    private List<String> options = new ArrayList<>();

}