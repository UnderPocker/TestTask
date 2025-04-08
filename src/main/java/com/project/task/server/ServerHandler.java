package com.project.task.server;

import com.project.task.model.Topic;
import com.project.task.model.VotingData;
import com.project.task.utils.VotingStorage;
import io.netty.channel.*;
import java.util.*;

public class ServerHandler extends SimpleChannelInboundHandler<String> {

    // Хранилище: кто залогинился
    private static final Map<ChannelId, String> loggedInUsers = new HashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        msg = msg.trim();
        ChannelId id = ctx.channel().id();

        if (msg.startsWith("login -u=")) {
            String username = msg.substring("login -u=".length()).trim();
            if (username.isEmpty()) {
                ctx.writeAndFlush("Имя пользователя не может быть пустым\n");
                return;
            }
            loggedInUsers.put(id, username);
            ctx.writeAndFlush("Добро пожаловать, " + username + "!\n");
            return;
        }

        // Проверка логина
        if (!loggedInUsers.containsKey(id)) {
            ctx.writeAndFlush("Пожалуйста, сначала выполните login -u=имя\n");
            return;
        }

        if (msg.startsWith("create topic -n=")) {
            String topicName = msg.substring("create topic -n=".length()).trim();
            if (topicName.isEmpty()) {
                ctx.writeAndFlush("Имя темы не может быть пустым\n");
                return;
            }

            VotingData data = VotingStorage.getData();
            if (data.getTopics().containsKey(topicName)) {
                ctx.writeAndFlush("Тема \"" + topicName + "\" уже существует\n");
                return;
            }

            Topic topic = new Topic();
            topic.setName(topicName);
            data.getTopics().put(topicName, topic);

            try {
                VotingStorage.save();
                ctx.writeAndFlush("Тема \"" + topicName + "\" успешно создана\n");
            } catch (Exception e) {
                ctx.writeAndFlush("Ошибка при сохранении темы: " + e.getMessage() + "\n");
            }

            return;
        }

        if (msg.equals("view")) {
            VotingData data = VotingStorage.getData();

            if (data.getTopics().isEmpty()) {
                ctx.writeAndFlush("Нет созданных тем\n");
                return;
            }

            StringBuilder response = new StringBuilder();
            for (Topic topic : data.getTopics().values()) {
                int voteCount = topic.getVotes() == null ? 0 : topic.getVotes().size();
                response.append(topic.getName())
                        .append(" (votes in topic=")
                        .append(voteCount)
                        .append(")\n");
            }

            ctx.writeAndFlush(response.toString());
            return;
        }

        String user = loggedInUsers.get(id);
        ctx.writeAndFlush("[" + user + "] команда не распознана: " + msg + "\n");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        loggedInUsers.remove(ctx.channel().id()); // очистка при выходе
    }
}