package com.example.prepandchill;

public class Subject {
    private String name;
    private String examDate;
    private boolean isSelected;

    public Subject(String name, String examDate, boolean isSelected) {
        this.name = name;
        this.examDate = examDate;
        this.isSelected = isSelected;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExamDate() {
        return examDate;
    }

    public void setExamDate(String examDate) {
        this.examDate = examDate;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}