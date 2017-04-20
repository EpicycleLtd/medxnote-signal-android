package com.medxnote.securesms.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;

public class MenuRecord {

    @JsonProperty("id") private Long id;
    @JsonProperty("rows") private List<Row> rows;
    @JsonProperty("style") private HashMap<String, String> style;
    @JsonProperty("input") private Boolean input = false;

    public MenuRecord() {
    }

    public MenuRecord(Long id, List<Row> rows, HashMap<String, String> style, Boolean input) {
        this.id = id;
        this.rows = rows;
        this.style = style;
        this.input = input;
    }

    private MenuRecord(Builder builder) {
        id = builder.id;
        rows = builder.rows;
        style = builder.style;
        input = builder.input;
    }

    public Long getId() {
        return id;
    }

    public List<Row> getRows() {
        return rows;
    }

    public HashMap<String, String> getStyle() {
        return style;
    }

    public Boolean hasInput() {
        return input;
    }

    @Override
    public String toString() {
        return "MenuRecord{" +
                "id=" + id +
                ", rows=" + rows +
                ", style=" + style +
                ", input=" + input +
                '}';
    }

    public static final class Builder {
        private Long id;
        private List<Row> rows;
        private HashMap<String, String> style;
        private Boolean input;

        public Builder() {
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder rows(List<Row> rows) {
            this.rows = rows;
            return this;
        }

        public Builder style(HashMap<String, String> style) {
            this.style = style;
            return this;
        }

        public Builder input(Boolean input) {
            this.input = input;
            return this;
        }

        public MenuRecord build() {
            return new MenuRecord(this);
        }
    }
}
