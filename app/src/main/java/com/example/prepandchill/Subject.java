package com.example.prepandchill;

import java.io.Serializable;

public class Subject implements Serializable {

    private int id; // 🔥 DB reference
    private String name;
    private String examDate;
    private boolean isSelected;
    private int proficiency; // 0–100
    private int difficulty;  // 1 easy, 2 medium, 3 hard

    //  Updated constructor
    public Subject(int id, String name, String examDate, boolean isSelected) {
        this.id = id;
        this.name = name;
        this.examDate = examDate;
        this.isSelected = isSelected;
        this.proficiency = 0;
        this.difficulty = 2; // default = medium
    }

    //  Optional constructor (when ID not needed)
    public Subject(String name, String examDate, boolean isSelected) {
        this.name = name;
        this.examDate = examDate;
        this.isSelected = isSelected;
        this.proficiency = 0;
        this.difficulty = 2;
    }

    //  GETTERS & SETTERS

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }
}