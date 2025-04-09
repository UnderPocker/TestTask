package com.project.task.server;

import com.project.task.model.Topic;
import com.project.task.model.Vote;
import com.project.task.model.VotingData;
import com.project.task.utils.VotingStorage;
import io.netty.channel.*;
import io.netty.util.AttributeKey;

import java.util.*;

public class ServerHandler extends SimpleChannelInboundHandler<String> {

    // Хранилище: кто залогинился
    private static final Map<ChannelId, String> loggedInUsers = new HashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        AttributeKey<ClientSession> sessionKey = AttributeKey.valueOf("session");
        ClientSession session = ctx.channel().attr(sessionKey).get();

        msg = msg.trim();

        switch (session.getState()) {
            case CREATING_VOTE_NAME:
                session.setVoteName(msg.trim());
                session.setState(ClientSession.State.CREATING_VOTE_DESCRIPTION);
                ctx.writeAndFlush("Введите описание голосования:\n");
                return;

            case CREATING_VOTE_DESCRIPTION:
                session.setVoteDescription(msg.trim());
                session.setState(ClientSession.State.CREATING_VOTE_OPTIONS_COUNT);
                ctx.writeAndFlush("Введите количество вариантов ответа:\n");
                return;

            case CREATING_VOTE_OPTIONS_COUNT:
                try {
                    int count = Integer.parseInt(msg.trim());
                    if (count <= 0) throw new NumberFormatException();
                    session.setOptionsCount(count);
                    session.getOptions().clear(); // на случай повторного ввода
                    session.setState(ClientSession.State.CREATING_VOTE_OPTIONS);
                    ctx.writeAndFlush("Введите вариант ответа 1:\n");
                } catch (NumberFormatException e) {
                    ctx.writeAndFlush("Введите корректное положительное число:\n");
                }
                return;

            case CREATING_VOTE_OPTIONS:
                session.getOptions().add(msg.trim());
                if (session.getOptions().size() < session.getOptionsCount()) {
                    ctx.writeAndFlush("Введите вариант ответа " + (session.getOptions().size() + 1) + ":\n");
                } else {
                    // Завершаем создание голосования
                    Vote vote = new Vote();
                    vote.setName(session.getVoteName());
                    vote.setDescription(session.getVoteDescription());
                    vote.setOptions(session.getOptions());
                    vote.setCreatedBy(ServerHandler.getLoggedInUsername(ctx.channel().id()));

                    Topic topic = VotingStorage.getData().getTopics().get(session.getTopicName());
                    if (topic != null) {
                        topic.getVotes().put(session.getVoteName(), vote);
                        ctx.writeAndFlush("Голосование успешно создано!\n");
                    } else {
                        ctx.writeAndFlush("Ошибка: раздел не найден.\n");
                    }

                    // Сброс состояния
                    session.setState(ClientSession.State.DEFAULT);
                    session.setVoteName(null);
                    session.setVoteDescription(null);
                    session.setOptionsCount(0);
                    session.getOptions().clear();
                }
                return;
            case VOTING_CHOICE:
                String topicName = session.getTopicName();
                String voteName = session.getVoteName();
                Topic topic = VotingStorage.getData().getTopics().get(topicName);
                Vote vote = topic.getVotes().get(voteName);

                List<String> options = vote.getOptions();
                int choice;
                try {
                    choice = Integer.parseInt(msg.trim());
                } catch (NumberFormatException e) {
                    ctx.writeAndFlush("Введите номер варианта ответа (1, 2, ...)\n");
                    return;
                }

                if (choice < 1 || choice > options.size()) {
                    ctx.writeAndFlush("Неверный номер варианта. Повторите ввод.\n");
                    return;
                }

                String selectedOption = options.get(choice - 1);
                vote.getResults().merge(selectedOption, 1, Integer::sum);

                ctx.writeAndFlush("Голос засчитан за: " + selectedOption + "\n");
                session.setState(ClientSession.State.DEFAULT);
                return;
            default:
                // все остальные команды в обычном режиме
                handleDefaultCommand(ctx, msg.trim(), session);
        }




    }

    private void handleDefaultCommand(ChannelHandlerContext ctx, String command, ClientSession session) {
        ChannelId id = ctx.channel().id();
        if (command.startsWith("login -u=")) {
            handleLogging(ctx, command, id);
            return;
        }

        // Проверка логина
        if (!loggedInUsers.containsKey(id)) {
            ctx.writeAndFlush("Пожалуйста, сначала выполните login -u=имя\n");
            return;
        }

        //исполнение команд
        if (command.startsWith("create topic -n=")) {
            handleCreatingTopic(ctx, command);
        }else if (command.equals("view")) {
            handleDefaultView(ctx);
        }else if (command.startsWith("create vote -t=")) {
            handleCreatingVote(ctx, command, session);
        }else if (command.startsWith("view -t=") && !command.contains(" -v=")) {
            handleViewTopic(ctx, command);
        }else if(command.startsWith("view -t=") && command.contains(" -v=")){
            handleViewVote(ctx, command);
        }else if (command.startsWith("vote -t=") && command.contains(" -v=")) {
            handleVoting(ctx, command, session);
        }else if (command.startsWith("delete -t=") && command.contains(" -v=")){
            handleVoteDelete(ctx, command, session);
        }else if (command.equals("exit")){
            handleExit(ctx);
        }else{
            String user = loggedInUsers.get(id);
            ctx.writeAndFlush("[" + user + "] команда не распознана: " + command + "\n");
        }
    }

    private void handleLogging(ChannelHandlerContext ctx, String command, ChannelId id){
        String username = command.substring("login -u=".length()).trim();
        if (username.isEmpty()) {
            ctx.writeAndFlush("Имя пользователя не может быть пустым\n");
            return;
        }
        loggedInUsers.put(id, username);
        ctx.writeAndFlush("Добро пожаловать, " + username + "!\n");
    }

    private void handleCreatingTopic(ChannelHandlerContext ctx, String command){
        String topicName = command.substring("create topic -n=".length()).trim();
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
    }

    private void handleDefaultView(ChannelHandlerContext ctx){
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
    }

    private void handleCreatingVote(ChannelHandlerContext ctx, String command, ClientSession session){
        String topicName = command.substring("create vote -t=".length()).trim();
        if (topicName.isEmpty()) {
            ctx.writeAndFlush("Укажите имя раздела через -t=имя\n");
            return;
        }

        Topic topic = VotingStorage.getData().getTopics().get(topicName);
        if (topic == null) {
            ctx.writeAndFlush("Раздел \"" + topicName + "\" не найден.\n");
            return;
        }

        // Настраиваем сессию на создание голосования
        session.setTopicName(topicName);
        session.setState(ClientSession.State.CREATING_VOTE_NAME);
        ctx.writeAndFlush("Введите название голосования:\n");
    }

    private void handleViewTopic(ChannelHandlerContext ctx, String command){
        String topicName = command.substring("view -t=".length()).trim();
        Topic topic = VotingStorage.getData().getTopics().get(topicName);
        if (topic == null) {
            ctx.writeAndFlush("Раздел \"" + topicName + "\" не найден\n");
            return;
        }

        StringBuilder response = new StringBuilder("Голосования в разделе \"" + topicName + "\":\n");
        for (Vote vote : topic.getVotes().values()) {
            response.append("- ").append(vote.getName()).append(": ").append(vote.getDescription()).append("\n");
        }

        if (topic.getVotes().isEmpty()) {
            response.append("(пока нет голосований)\n");
        }

        ctx.writeAndFlush(response.toString());
    }


    private void handleViewVote(ChannelHandlerContext ctx, String command){
        // Извлекаем topic и vote
        String[] parts = command.split(" -v=");
        String topicPart = parts[0].substring("view -t=".length()).trim();
        String votePart = parts.length > 1 ? parts[1].trim() : "";

        if (topicPart.isEmpty() || votePart.isEmpty()) {
            ctx.writeAndFlush("Укажите и раздел, и имя голосования: view -t=<topic> -v=<vote>\n");
            return;
        }

        Topic topic = VotingStorage.getData().getTopics().get(topicPart);
        if (topic == null) {
            ctx.writeAndFlush("Раздел \"" + topicPart + "\" не найден.\n");
            return;
        }

        Vote vote = topic.getVotes().get(votePart);
        if (vote == null) {
            ctx.writeAndFlush("Голосование \"" + votePart + "\" не найдено в разделе \"" + topicPart + "\".\n");
            return;
        }

        StringBuilder response = new StringBuilder();
        response.append("Тема голосования: ").append(vote.getDescription()).append("\n");
        response.append("Варианты ответа:\n");

        for (String option : vote.getOptions()) {
            int count = vote.getResults().getOrDefault(option, 0);
            response.append("- ").append(option).append(" (").append(count).append(" голосов)\n");
        }

        ctx.writeAndFlush(response.toString());
    }

    private void handleVoting(ChannelHandlerContext ctx, String command, ClientSession session){
        String[] parts = command.split(" -v=");
        String topicName = parts[0].substring("vote -t=".length()).trim();
        String voteName = parts.length > 1 ? parts[1].trim() : "";

        if (topicName.isEmpty() || voteName.isEmpty()) {
            ctx.writeAndFlush("Укажите и раздел, и голосование: vote -t=<topic> -v=<vote>\n");
            return;
        }

        Topic topic = VotingStorage.getData().getTopics().get(topicName);
        if (topic == null) {
            ctx.writeAndFlush("Раздел \"" + topicName + "\" не найден.\n");
            return;
        }

        Vote vote = topic.getVotes().get(voteName);
        if (vote == null) {
            ctx.writeAndFlush("Голосование \"" + voteName + "\" не найдено в разделе \"" + topicName + "\".\n");
            return;
        }

        // Сохраняем в сессию, переключаем состояние
        session.setTopicName(topicName);
        session.setVoteName(voteName);
        session.setState(ClientSession.State.VOTING_CHOICE);

        // Показываем варианты
        StringBuilder sb = new StringBuilder("Выберите вариант ответа:\n");
        int index = 1;
        for (String option : vote.getOptions()) {
            sb.append(index).append(". ").append(option).append("\n");
            index++;
        }

        ctx.writeAndFlush(sb.toString());
    }

    private void handleVoteDelete(ChannelHandlerContext ctx, String command, ClientSession session){
        String[] parts = command.split(" -v=");
        String topicName = parts[0].substring("delete -t=".length()).trim();
        String voteName = parts.length > 1 ? parts[1].trim() : "";

        if (topicName.isEmpty() || voteName.isEmpty()) {
            ctx.writeAndFlush("Укажите и раздел, и голосование: delete -t=<topic> -v=<vote>\n");
            return;
        }

        Topic topic = VotingStorage.getData().getTopics().get(topicName);
        if (topic == null) {
            ctx.writeAndFlush("Раздел \"" + topicName + "\" не найден.\n");
            return;
        }

        Vote vote = topic.getVotes().get(voteName);
        if (vote == null) {
            ctx.writeAndFlush("Голосование \"" + voteName + "\" не найдено в разделе \"" + topicName + "\".\n");
            return;
        }

        if (!loggedInUsers.get(ctx.channel().id()).equals(vote.getCreatedBy())) {
            ctx.writeAndFlush("Удалить голосование может только его создатель (" + vote.getCreatedBy() + ").\n");
            return;
        }

        topic.getVotes().remove(voteName);
        ctx.writeAndFlush("Голосование \"" + voteName + "\" удалено из раздела \"" + topicName + "\".\n");

    }

    private void handleExit(ChannelHandlerContext ctx){
        String username = loggedInUsers.remove(ctx.channel().id());
        ctx.writeAndFlush("До свидания, " + (username != null ? username : "пользователь") + "!\n");

        // Закрываем соединение
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        AttributeKey<ClientSession> sessionKey = AttributeKey.valueOf("session");
        ctx.channel().attr(sessionKey).set(new ClientSession());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        loggedInUsers.remove(ctx.channel().id()); // очистка при выходе
    }

    public static String getLoggedInUsername(ChannelId id) {
        return loggedInUsers.get(id);
    }
}