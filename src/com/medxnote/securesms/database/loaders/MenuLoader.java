package com.medxnote.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.util.AbstractCursorLoader;


public class MenuLoader extends AbstractCursorLoader {

    public static final Integer ID = 1;

    private long threadId;

    public MenuLoader(Context context, long threadId) {
        super(context);
        this.threadId = threadId;
    }

    @Override
    public Cursor getCursor() {
        return DatabaseFactory.getMenuDatabase(getContext()).query(threadId);
    }
}
