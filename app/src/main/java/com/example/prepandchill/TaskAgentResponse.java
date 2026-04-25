package com.example.prepandchill;

import java.util.List;

public class TaskAgentResponse {
    private final String action;
    private final List<TaskItem> tasks;

    public TaskAgentResponse(String action, List<TaskItem> tasks) {
        this.action = action;
        this.tasks = tasks;
    }

    public String getAction() {
        return action;
    }

    public List<TaskItem> getTasks() {
        return tasks;
    }
}
