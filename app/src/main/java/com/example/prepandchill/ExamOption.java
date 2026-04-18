package com.example.prepandchill;

public class ExamOption {
    private String emoji;
    private String name;
    private boolean isSelected;

    public ExamOption(String emoji, String name, boolean isSelected) {
        this.emoji = emoji;
        this.name = name;
        this.isSelected = isSelected;
    }

    public String getEmoji() { return emoji; }
    public String getName() { return name; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}