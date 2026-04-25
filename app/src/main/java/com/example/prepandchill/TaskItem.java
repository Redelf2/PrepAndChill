package com.example.prepandchill;

public class TaskItem {
    private final String title;
    private final String duration;

    public TaskItem(String title, String duration) {
        this.title = title;
        this.duration = duration;
    }

    public String getTitle() {
        return title;
    }

    public String getDuration() {
        return duration;
    }
}
