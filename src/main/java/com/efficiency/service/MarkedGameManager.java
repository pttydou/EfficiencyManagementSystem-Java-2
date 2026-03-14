package com.efficiency.service;

import com.efficiency.model.MarkedGame;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * 标记游戏进程管理器
 * 对应原C++ MarkedGameManager类
 * 使用JSON文件持久化存储
 */
public class MarkedGameManager {
    private final String jsonFilePath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // 回调（替代Qt信号）
    private Consumer<List<MarkedGame>> onMarkedListUpdated;

    public MarkedGameManager() {
        this.jsonFilePath = System.getProperty("user.dir") + File.separator + "marked_games.json";
        initJsonFile();
    }

    public MarkedGameManager(String jsonPath) {
        this.jsonFilePath = jsonPath;
        initJsonFile();
    }

    public void setOnMarkedListUpdated(Consumer<List<MarkedGame>> callback) {
        this.onMarkedListUpdated = callback;
    }

    private void initJsonFile() {
        System.out.println("Marked games JSON path: " + jsonFilePath);
        File file = new File(jsonFilePath);
        if (!file.exists()) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                JsonObject root = new JsonObject();
                root.add("marked_games", new JsonArray());
                writer.write(gson.toJson(root));
                System.out.println("Created empty marked games JSON file.");
            } catch (IOException e) {
                System.err.println("创建JSON文件失败: " + e.getMessage());
            }
        }
    }

    public List<MarkedGame> loadMarkedGames() {
        List<MarkedGame> games = new ArrayList<>();
        File file = new File(jsonFilePath);
        if (!file.exists()) return games;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("marked_games");
            if (arr != null) {
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    MarkedGame game = new MarkedGame();
                    game.setProcessName(obj.has("processName") ? obj.get("processName").getAsString() : "");
                    game.setMarkedTime(obj.has("markedTime") ? obj.get("markedTime").getAsString() : "");
                    game.setDescription(obj.has("description") ? obj.get("description").getAsString() : "");
                    game.setAutoMonitor(obj.has("autoMonitor") && obj.get("autoMonitor").getAsBoolean());
                    games.add(game);
                }
            }
        } catch (Exception e) {
            System.err.println("加载标记游戏失败: " + e.getMessage());
        }
        return games;
    }

    private boolean saveToJsonFile(List<MarkedGame> games) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(jsonFilePath), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (MarkedGame game : games) {
                JsonObject obj = new JsonObject();
                obj.addProperty("processName", game.getProcessName());
                obj.addProperty("markedTime", game.getMarkedTime());
                obj.addProperty("description", game.getDescription());
                obj.addProperty("autoMonitor", game.isAutoMonitor());
                arr.add(obj);
            }
            root.add("marked_games", arr);
            writer.write(gson.toJson(root));
            return true;
        } catch (IOException e) {
            System.err.println("保存JSON文件失败: " + e.getMessage());
            return false;
        }
    }

    public boolean addMarkedGame(String processName, String description, boolean autoMonitor) {
        List<MarkedGame> existing = loadMarkedGames();
        for (MarkedGame g : existing) {
            if (g.getProcessName().equalsIgnoreCase(processName)) {
                System.out.println("Process already marked: " + processName);
                return false;
            }
        }

        MarkedGame newGame = new MarkedGame();
        newGame.setProcessName(processName);
        newGame.setMarkedTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        newGame.setDescription(description);
        newGame.setAutoMonitor(autoMonitor);
        existing.add(newGame);

        boolean saved = saveToJsonFile(existing);
        if (saved && onMarkedListUpdated != null) {
            onMarkedListUpdated.accept(existing);
        }
        return saved;
    }

    public boolean removeMarkedGame(String processName) {
        List<MarkedGame> existing = loadMarkedGames();
        boolean removed = existing.removeIf(g -> g.getProcessName().equalsIgnoreCase(processName));
        if (!removed) {
            System.out.println("Process not found: " + processName);
            return false;
        }

        boolean saved = saveToJsonFile(existing);
        if (saved && onMarkedListUpdated != null) {
            onMarkedListUpdated.accept(existing);
        }
        return saved;
    }

    public List<String> getAllProcessNames() {
        List<String> names = new ArrayList<>();
        for (MarkedGame game : loadMarkedGames()) {
            names.add(game.getProcessName());
        }
        return names;
    }

    public String getJsonFilePath() {
        return jsonFilePath;
    }
}
