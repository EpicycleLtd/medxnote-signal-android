package com.medxnote.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.util.AbstractCursorLoader;

public class MessageEditHistoryLoader extends AbstractCursorLoader {

    private String type;
    private long messageId;

    public MessageEditHistoryLoader(Context context, String type, long messageId) {
        super(context);
        this.type = type;
        this.messageId = messageId;
    }

    @Override
    public Cursor getCursor() {
        return DatabaseFactory.getEditDatabase(context).query(messageId, type);
    }
}
