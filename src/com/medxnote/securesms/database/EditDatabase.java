package com.medxnote.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.medxnote.securesms.crypto.AsymmetricMasterCipher;
import com.medxnote.securesms.crypto.MasterCipher;
import com.medxnote.securesms.crypto.MasterSecretUnion;
import com.medxnote.securesms.database.documents.IdentityKeyMismatch;
import com.medxnote.securesms.database.model.DisplayRecord;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.database.model.SmsMessageRecord;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;

import org.whispersystems.libsignal.InvalidMessageException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class EditDatabase extends MessagingDatabase {

    private static final String TAG = EditDatabase.class.getSimpleName();

    public static final String TABLE_NAME = "edit_table";
    public static final String MESSAGE_ID = "message_id";
    public static final String THREAD_ID = "thread_id";
    public static final String TYPE = "type";
    public static final String TRANSPORT = "transport";
    public static final String DATE_SENT = "date_sent";
    public static final String DATE_EDIT = "date_edit";
    public static final String RECIPIENT_IDS = "recipient_ids";
    public static final String RECEIPT_COUNT = "receipt_count";
    public static final String STATUS = "status";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
            MESSAGE_ID + " INTEGER, " + THREAD_ID + " INTEGER, " +
            BODY + " TEXT, " + TRANSPORT + " TEXT, " + TYPE + " INTEGER DEFAULT 0, " +
            DATE_SENT + " INTEGER, " + DATE_EDIT + " INTEGER, " + RECIPIENT_IDS + " TEXT, " +
            RECEIPT_COUNT + " INTEGER DEFAULT 0, " + ADDRESS_DEVICE_ID + " INTEGER DEFAULT 1, " +
            STATUS + " INTEGER DEFAULT -1, " + SUBSCRIPTION_ID + " INTEGER DEFAULT -1);";

    public static final String[] PROJECTION = {
            ID, MESSAGE_ID, THREAD_ID, BODY,
            TRANSPORT, DATE_SENT, DATE_EDIT,
            TYPE, RECIPIENT_IDS, RECEIPT_COUNT,
            ADDRESS_DEVICE_ID, STATUS, SUBSCRIPTION_ID
    };

    public static final String[] CREATE_INDEXS = {
            "CREATE INDEX IF NOT EXISTS edit_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
            "CREATE INDEX IF NOT EXISTS edit_message_id_index ON " + TABLE_NAME + " (" + MESSAGE_ID + ");"
    };

    public EditDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    protected String getTableName() {
        return TABLE_NAME;
    }

    public long insert(@NonNull MasterSecretUnion masterSecretUnion,
                       @NonNull MessageRecord messageRecord) {

        long[] recipientIds = getRecipientIds(messageRecord.getRecipients());
        String recipientString = getRecipientsAsString(recipientIds);

        ContentValues contentValues = new ContentValues(10);
            contentValues.put(MESSAGE_ID, messageRecord.getId());
            contentValues.put(THREAD_ID, messageRecord.getThreadId());
            contentValues.put(BODY, getEncryptedBody(masterSecretUnion, messageRecord.getBody().getBody()));
            contentValues.put(TYPE, messageRecord.getType());
            contentValues.put(RECIPIENT_IDS, recipientString);
            contentValues.put(RECEIPT_COUNT, messageRecord.getReceiptCount());
            contentValues.put(SUBSCRIPTION_ID, messageRecord.getSubscriptionId());
            contentValues.put(TRANSPORT, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
            contentValues.put(DATE_SENT, messageRecord.getTimestamp());
            contentValues.put(DATE_EDIT, System.currentTimeMillis());

        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        return database.insert(TABLE_NAME, null, contentValues);
    }

    public Cursor query() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        return database.query(TABLE_NAME, PROJECTION, null, null, null, null, null);
    }

    public Cursor query(long messageId, String type) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        String selection = MESSAGE_ID + " = ? AND " + TRANSPORT + " = ?";
        return database.query(TABLE_NAME, PROJECTION, selection, new String[] {String.valueOf(messageId), type}, null, null, null);
    }

    public long delete() {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        return database.delete(TABLE_NAME, null, null);
    }

    public long deleteThread(long threadId) {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        String where = THREAD_ID + " = ?";
        return database.delete(TABLE_NAME, where, new String[] {String.valueOf(threadId)});
    }

    public void delete(Set<Long> threadIds) {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        String where = THREAD_ID + " = ?";
        for (Long id : threadIds) {
            database.delete(TABLE_NAME, where, new String[] {String.valueOf(id)});
        }
    }

    public long delete(long messageId, String type) {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        String where = MESSAGE_ID + " = ? AND " + TRANSPORT + " = ?";
        return database.delete(TABLE_NAME, where, new String[]{String.valueOf(messageId), type});
    }

    private long[] getRecipientIds(Recipients recipients) {
        Set<Long> recipientSet  = new HashSet<>();
        List<Recipient> recipientList = recipients.getRecipientsList();

        for (Recipient recipient : recipientList) {
            recipientSet.add(recipient.getRecipientId());
        }

        long[] recipientArray = new long[recipientSet.size()];
        int i                 = 0;

        for (Long recipientId : recipientSet) {
            recipientArray[i++] = recipientId;
        }

        Arrays.sort(recipientArray);

        return recipientArray;
    }

    private String getRecipientsAsString(long[] recipientIds) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<recipientIds.length;i++) {
            if (i != 0) sb.append(' ');
            sb.append(recipientIds[i]);
        }

        return sb.toString();
    }

    private String getEncryptedBody(MasterSecretUnion masterSecret, String body) {
        if (masterSecret.getMasterSecret().isPresent()) {
            return new MasterCipher(masterSecret.getMasterSecret().get()).encryptBody(body);
        } else {
            return new AsymmetricMasterCipher(masterSecret.getAsymmetricMasterSecret().get()).encryptBody(body);
        }
    }

    public Reader readerFor(Cursor cursor, MasterCipher masterCipher) {
        return new Reader(cursor, masterCipher);
    }

    public class Reader {

        private Cursor cursor;
        private MasterCipher masterCipher;

        public Reader(Cursor cursor, MasterCipher masterCipher) {
            this.cursor = cursor;
            this.masterCipher = masterCipher;
        }

        public SmsMessageRecord getNext() {
            if (cursor == null || !cursor.moveToNext())
                return null;

            return getCurrent();
        }

        public SmsMessageRecord getCurrent() {
            long messageId = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_ID));
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
            DisplayRecord.Body body = getPlaintextBody(cursor);
            long dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(DATE_SENT));
            long dateEdit = cursor.getLong(cursor.getColumnIndexOrThrow(DATE_EDIT));
            int addressDeviceId = cursor.getInt(cursor.getColumnIndexOrThrow(ADDRESS_DEVICE_ID));
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(STATUS));
            int subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(SUBSCRIPTION_ID));

            Recipients recipients = RecipientFactory.getRecipientsForIds(context, cursor.getString(cursor.getColumnIndexOrThrow(RECIPIENT_IDS)), false);

            int receiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(RECEIPT_COUNT));
            int type = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));

            return new SmsMessageRecord(context, messageId, body, recipients, recipients.getPrimaryRecipient(),
                    addressDeviceId, dateEdit, dateEdit, dateEdit, receiptCount, type, threadId, status,
                    new LinkedList<IdentityKeyMismatch>(), subscriptionId);
        }

        private DisplayRecord.Body getPlaintextBody(Cursor cursor) {
            try {

                int type = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
                String body = cursor.getString(cursor.getColumnIndexOrThrow(BODY));

                if (!TextUtils.isEmpty(body) && masterCipher != null && MmsSmsColumns.Types.isSymmetricEncryption(type)) {
                    return new DisplayRecord.Body(masterCipher.decryptBody(body), true);
                } else if (!TextUtils.isEmpty(body) && masterCipher == null && MmsSmsColumns.Types.isSymmetricEncryption(type)) {
                    return new DisplayRecord.Body(body, false);
                } else {
                    return new DisplayRecord.Body(body, true);
                }
            } catch (InvalidMessageException e) {
                Log.w(TAG, e);
                return new DisplayRecord.Body("Error", true);
            }
        }

    }
}
