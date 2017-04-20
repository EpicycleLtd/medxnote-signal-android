package com.medxnote.securesms.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;

public class Row {

    @JsonProperty("cells") private List<Cell> cells;
    @JsonProperty("style") private HashMap<String, String> style;

    public Row() {
    }

    public Row(List<Cell> cells, HashMap<String, String> style) {
        this.cells = cells;
        this.style = style;
    }

    private Row(Builder builder) {
        cells = builder.cells;
        style = builder.style;
    }

    public List<Cell> getCells() {
        return cells;
    }

    public Integer getSize() {
        return cells.size();
    }

    public HashMap<String, String> getStyle() {
        return style;
    }

    @Override
    public String toString() {
        return "Row{" +
                "cells=" + cells +
                ", style=" + style +
                '}';
    }

    public static final class Builder {
        private List<Cell> cells;
        private HashMap<String, String> style;

        public Builder() {
        }

        public Builder cells(List<Cell> cells) {
            this.cells = cells;
            return this;
        }

        public Builder style(HashMap<String, String> style) {
            this.style = style;
            return this;
        }

        public Row build() {
            return new Row(this);
        }
    }
}
