package com.example.prepandchill;

import java.io.Serializable;

public class Subject implements Serializable {
    private String name;
    private String examDate;
    private boolean isSelected;
    private int proficiency; // 0-100

    public Subject(String name, String examDate, boolean isSelected) {
        this.name = name;
        this.examDate = examDate;
        this.isSelected = isSelected;
        this.proficiency = 0;
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

    public int getProficiency() {
        return proficiency;
    }

    public void setProficiency(int proficiency) {
        this.proficiency = proficiency;
    }
}