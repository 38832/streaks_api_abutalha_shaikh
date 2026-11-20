package com.streaks.api.habit;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CreateHabitRequest {

    @JsonAlias({"name"})
    private String title;

    public CreateHabitRequest() {
    }

    public CreateHabitRequest(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
