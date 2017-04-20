package com.medxnote.securesms.events;


public class DatabaseEvent {

    public enum Type {
        BUBBLE,
        MENU
    }

    private Type type;
    private Long threadId;

    public DatabaseEvent(Type type, Long threadId) {
        this.type = type;
        this.threadId = threadId;
    }

    public Type getType() {
        return type;
    }

    public Long getThreadId() {
        return threadId;
    }

}
