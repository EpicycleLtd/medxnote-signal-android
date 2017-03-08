package com.medxnote.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.medxnote.securesms.database.model.MenuRecord;
import com.medxnote.securesms.events.DatabaseEvent;
import com.medxnote.securesms.util.JsonUtils;

import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.util.List;

import de.greenrobot.event.EventBus;

// TODO: need to create one table for bubbles & menus
public class MenuDatabase extends MessagingDatabase {

    private static final String TAG = MenuDatabase.class.getSimpleName();

    public static final String TABLE_NAME = "menu";

    public static final String THREAD_ID = "thread_id";
    public static final String RAW_DATA = "raw_data";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " " +
            "(" +
                ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                THREAD_ID + " INTEGER NOT NULL, " +
                RAW_DATA + " TEXT NOT NULL " +
            ");";

    private static final String[] MENU_PROJECTION =  {
            ID, THREAD_ID, RAW_DATA
    };

    @Override
    protected String getTableName() {
        return TABLE_NAME;
    }

    public MenuDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public void insert(Long threadId, String rawData) {
        // delete(threadId);
        ContentValues values = new ContentValues(2);
            if (rawData != null) {
                values.put(THREAD_ID, threadId);
                values.put(RAW_DATA, rawData);
            }
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        long rowId = db.insert(TABLE_NAME, null, values);

        if (rowId != -1) {
            sendEvent(threadId);
        }
    }

    public Cursor query(Long threadId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        return db.query(TABLE_NAME, MENU_PROJECTION, THREAD_ID + " = ?", new String[] {String.valueOf(threadId)}, null, null, null);
    }

    public Cursor query() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        return db.query(TABLE_NAME, MENU_PROJECTION, null, null, null, null, null);
    }

    public int delete(long threadId) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        return db.delete(TABLE_NAME, THREAD_ID + " = ?", new String[] {String.valueOf(threadId)});
    }

    public Reader readerFor(Cursor cursor) {
        return new Reader(cursor);
    }

    private void sendEvent(long threadId) {
        EventBus.getDefault().postSticky(new DatabaseEvent(DatabaseEvent.Type.MENU, threadId));
    }

    public class Reader {
        private final Cursor cursor;
        public Reader(Cursor cursor) {
            this.cursor = cursor;
        }
        public int getCount() {
            if (cursor == null) return 0;
            else                return cursor.getCount();
        }
        public List<MenuRecord> getCurrent() {
            List<MenuRecord> menus = null;
            try {
                while (cursor != null && cursor.moveToNext()) {
                    menus = JsonUtil.fromJson(cursor.getString(cursor.getColumnIndexOrThrow(RAW_DATA)),
                            new TypeReference<List<MenuRecord>>() {});
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return menus;
        }

        public void close() {
            cursor.close();
        }
    }
}
