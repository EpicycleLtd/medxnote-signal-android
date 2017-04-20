package com.medxnote.securesms.database.model;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;

public class Cell {

    @JsonProperty("cmd") private String cmd;
    @JsonProperty("title") private String title;
    @JsonProperty("link") private String link;
    @JsonProperty("echo") private Boolean echo;
    @JsonProperty("input") private Boolean input;
    @JsonProperty("style") private HashMap<String, String> style;

    public Cell() {
    }

    public Cell(String cmd, String title, String link, Boolean echo, Boolean input, HashMap<String, String> style) {
        this.cmd = cmd;
        this.title = title;
        this.link = link;
        this.echo = echo;
        this.input = input;
        this.style = style;
    }

    private Cell(Builder builder) {
        cmd = builder.cmd;
        title = builder.title;
        link = builder.link;
        echo = builder.echo;
        input = builder.input;
        style = builder.style;
    }

    public String getCmd() {
        return cmd;
    }

    public String getTitle() {
        return title;
    }

    public String getLink() {
        return link;
    }

    public Boolean getEcho() {
        return echo;
    }

    public Boolean isInput() {
        return !(input == null || !input);
    }

    public HashMap<String, String> getStyle() {
        return style;
    }


    public static final class Builder {
        private String cmd;
        private String title;
        private String link;
        private Boolean echo;
        private Boolean input;
        private HashMap<String, String> style;

        public Builder() {
        }

        public Builder cmd(String cmd) {
            this.cmd = cmd;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder link(String link) {
            this.link = link;
            return this;
        }

        public Builder echo(Boolean echo) {
            this.echo = echo;
            return this;
        }

        public Builder input(Boolean input) {
            this.input = input;
            return this;
        }

        public Builder style(HashMap<String, String> style) {
            this.style = style;
            return this;
        }

        public Cell build() {
            return new Cell(this);
        }
    }
}
