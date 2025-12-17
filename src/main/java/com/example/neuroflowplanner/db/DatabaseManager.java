package com.example.neuroflowplanner.db;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.util.DataPathManager;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseManager {
    private static final String DB_URL = DataPathManager.getDatabaseUrl();
    private static DatabaseManager instance;
    
    private DatabaseManager() {
        initDatabase();
    }
    
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }
    
    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    deadline TEXT NOT NULL,
                    complexity INTEGER NOT NULL,
                    smart_priority REAL DEFAULT 0,
                    ai_insight TEXT,
                    parent_id TEXT,
                    tags TEXT DEFAULT '',
                    FOREIGN KEY (parent_id) REFERENCES tasks(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS task_templates (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    complexity INTEGER NOT NULL,
                    days_until_deadline INTEGER DEFAULT 7,
                    tags TEXT DEFAULT ''
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mood_entries (
                    id TEXT PRIMARY KEY,
                    timestamp TEXT NOT NULL,
                    score INTEGER NOT NULL,
                    note TEXT,
                    analysis TEXT
                )
            """);
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN parent_id TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN tags TEXT DEFAULT ''"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN recurrence TEXT DEFAULT ''"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN depends_on TEXT DEFAULT ''"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN archived INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN tracked_minutes INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN start_date TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN completed INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE tasks ADD COLUMN completed_date TEXT"); } catch (SQLException ignored) {}
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void saveTask(Task task) {
        String sql = """
            INSERT OR REPLACE INTO tasks (id, title, description, deadline, complexity, smart_priority, ai_insight, parent_id, tags, recurrence, depends_on, archived, tracked_minutes, start_date, completed, completed_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, task.getId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getDescription());
            ps.setString(4, task.getDeadline().toString());
            ps.setInt(5, task.getComplexity());
            ps.setDouble(6, task.getSmartPriority());
            ps.setString(7, task.getAiInsight());
            ps.setString(8, task.getParentId());
            ps.setString(9, task.getTags());
            ps.setString(10, task.getRecurrence());
            ps.setString(11, task.getDependsOn());
            ps.setInt(12, task.isArchived() ? 1 : 0);
            ps.setLong(13, task.getTrackedMinutes());
            ps.setString(14, task.getStartDate() != null ? task.getStartDate().toString() : null);
            ps.setInt(15, task.isCompleted() ? 1 : 0);
            ps.setString(16, task.getCompletedDate() != null ? task.getCompletedDate().toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void deleteTask(String id) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id = ? OR parent_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public List<Task> loadAllTasks() {
        List<Task> allTasks = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM tasks")) {
            while (rs.next()) {
                String tags = rs.getString("tags");
                String recurrence = null;
                String dependsOn = null;
                int archived = 0;
                long trackedMinutes = 0;
                String startDateStr = null;
                try { recurrence = rs.getString("recurrence"); } catch (SQLException ignored) {}
                try { dependsOn = rs.getString("depends_on"); } catch (SQLException ignored) {}
                try { archived = rs.getInt("archived"); } catch (SQLException ignored) {}
                try { trackedMinutes = rs.getLong("tracked_minutes"); } catch (SQLException ignored) {}
                try { startDateStr = rs.getString("start_date"); } catch (SQLException ignored) {}
                int completed = 0;
                String completedDateStr = null;
                try { completed = rs.getInt("completed"); } catch (SQLException ignored) {}
                try { completedDateStr = rs.getString("completed_date"); } catch (SQLException ignored) {}
                Task task = new Task(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    LocalDate.parse(rs.getString("deadline")),
                    rs.getInt("complexity"),
                    rs.getString("parent_id"),
                    tags != null ? tags : "",
                    recurrence != null ? recurrence : ""
                );
                task.setSmartPriority(rs.getDouble("smart_priority"));
                task.setAiInsight(rs.getString("ai_insight"));
                task.setDependsOn(dependsOn != null ? dependsOn : "");
                task.setArchived(archived == 1);
                task.setTrackedMinutes(trackedMinutes);
                if (startDateStr != null && !startDateStr.isEmpty()) {
                    task.setStartDate(LocalDate.parse(startDateStr));
                }
                task.setCompleted(completed == 1);
                if (completedDateStr != null && !completedDateStr.isEmpty()) {
                    task.setCompletedDate(LocalDate.parse(completedDateStr));
                }
                allTasks.add(task);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        Map<String, Task> taskMap = allTasks.stream().collect(Collectors.toMap(Task::getId, t -> t));
        List<Task> rootTasks = new ArrayList<>();
        for (Task task : allTasks) {
            if (task.getParentId() != null && taskMap.containsKey(task.getParentId())) {
                taskMap.get(task.getParentId()).getSubtasks().add(task);
            } else {
                rootTasks.add(task);
            }
        }
        return rootTasks;
    }

    public void saveTemplate(TaskTemplate t) {
        String sql = "INSERT OR REPLACE INTO task_templates (id, name, title, description, complexity, days_until_deadline, tags) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getId());
            ps.setString(2, t.getName());
            ps.setString(3, t.getTitle());
            ps.setString(4, t.getDescription());
            ps.setInt(5, t.getComplexity());
            ps.setInt(6, t.getDaysUntilDeadline());
            ps.setString(7, t.getTags());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void deleteTemplate(String id) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement("DELETE FROM task_templates WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<TaskTemplate> loadAllTemplates() {
        List<TaskTemplate> templates = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM task_templates")) {
            while (rs.next()) {
                templates.add(new TaskTemplate(
                    rs.getString("id"), rs.getString("name"), rs.getString("title"),
                    rs.getString("description"), rs.getInt("complexity"),
                    rs.getInt("days_until_deadline"), rs.getString("tags")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return templates;
    }

    public void saveMoodEntry(com.example.neuroflowplanner.model.MoodEntry entry) {
        String sql = "INSERT OR REPLACE INTO mood_entries (id, timestamp, score, note, analysis) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getId());
            ps.setString(2, entry.getTimestamp().toString());
            ps.setInt(3, entry.getScore());
            ps.setString(4, entry.getNote());
            ps.setString(5, entry.getAnalysis());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<com.example.neuroflowplanner.model.MoodEntry> loadMoodHistory() {
        List<com.example.neuroflowplanner.model.MoodEntry> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM mood_entries ORDER BY timestamp DESC")) {
            while (rs.next()) {
                list.add(new com.example.neuroflowplanner.model.MoodEntry(
                    rs.getString("id"),
                    java.time.LocalDateTime.parse(rs.getString("timestamp")),
                    rs.getInt("score"),
                    rs.getString("note"),
                    rs.getString("analysis")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
}
