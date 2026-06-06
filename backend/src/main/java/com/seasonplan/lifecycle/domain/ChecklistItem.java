package com.seasonplan.lifecycle.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ChecklistItem {
    private String key;
    private boolean complete;

    public ChecklistItem() {
    }

    public ChecklistItem(String key, boolean complete) {
        this.key = key;
        this.complete = complete;
    }

    public String key() {
        return key;
    }

    public boolean complete() {
        return complete;
    }

    public void markComplete() {
        this.complete = true;
    }
}
