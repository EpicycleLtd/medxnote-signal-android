/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.medxnote.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.medxnote.securesms.ApplicationContext;
import com.medxnote.securesms.database.documents.IdentityKeyMismatch;
import com.medxnote.securesms.database.documents.IdentityKeyMismatchList;
import com.medxnote.securesms.database.model.DisplayRecord;
import com.medxnote.securesms.database.model.ReceiptsRecord;
import com.medxnote.securesms.database.model.SmsMessageRecord;
import com.medxnote.securesms.jobs.TrimThreadJob;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.sms.IncomingGroupMessage;
import com.medxnote.securesms.sms.IncomingTextMessage;
import com.medxnote.securesms.sms.OutgoingTextMessage;
import com.medxnote.securesms.util.JsonUtils;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.medxnote.securesms.util.Util.canonicalizeNumber;

/**
 * Database for storage of SMS receipts.
 *
 * @author Alexand Barabash
 */

public class ReceiptDatabase extends MessagingDatabase {

  private static final String TAG = ReceiptDatabase.class.getSimpleName();

  public  static final String TABLE_NAME         = "receipts";
  public  static final String MESSAGE            = "message"; // timestamp
          static final String DATE_RECEIVED      = "date";
          static final String DATE_SENT          = "date_sent";
          static final String DATE_READ          = "date_read";
          static final String ADDRESS            = "address";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
      " (" + ID + " integer PRIMARY KEY, " + MESSAGE + " TEXT, " +
      DATE_RECEIVED + " INTEGER, " + DATE_SENT + " INTEGER, " + DATE_READ + " INTEGER, " +
      ADDRESS + " TEXT );";

  private static final String[] MESSAGE_PROJECTION = new String[] {
      ID, MESSAGE, DATE_RECEIVED, DATE_SENT, DATE_READ, ADDRESS
  };

  private final JobManager jobManager;

  public ReceiptDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
    this.jobManager = ApplicationContext.getInstance(context).getJobManager();
  }

  protected String getTableName() {
    return TABLE_NAME;
  }

  private void setDate(SyncMessageId messageId, String field){
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL(
        "UPDATE " + TABLE_NAME +
            " SET " + field + " = " + messageId.getDeliveryTimestamp() +
            " WHERE " + MESSAGE + " = ? AND " +
            ADDRESS + " = ?",
        new String[]{
            messageId.getTimetamp() + "",
            messageId.getAddress()
        }
    );
    notifyConversationListListeners();
  }

  public void setDateRead(SyncMessageId messageId){
    setDate(messageId, DATE_READ);
  }

  public void setDateReceived(SyncMessageId messageId){
    setDate(messageId, DATE_RECEIVED);
  }

  public Boolean isGroupReceipt(SyncMessageId messageId){
    int count = getCountForMessage(messageId);
    if (count > 0){
      return true;
    }
    return false;
  }

  public int getCountUnreceivedMessage(SyncMessageId messageId){
    return getCountForMessage(messageId, DATE_RECEIVED + " < 0");
  }

  public int getCountReceivedMessage(SyncMessageId messageId){
    return getCountForMessage(messageId, DATE_RECEIVED + " > 0");
  }

  public int getCountUnreadMessage(SyncMessageId messageId){
    return getCountForMessage(messageId, DATE_READ + " < 0");
  }

  public int getCountReadMessage(SyncMessageId messageId){
      return getCountForMessage(messageId, DATE_READ + " > 0");
  }

  public int getCountForMessage(SyncMessageId messageId){
      return getCountForMessage(messageId, null);
  }

  public int getCountForMessage(SyncMessageId messageId, String where){
      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      Cursor cursor     = null;
      String selection = MESSAGE + " = ?";
      if (where != null){ selection = MESSAGE + " = ?" + " AND " + where; }

      try {
          Log.w(TAG, "creating cursor", new Exception());
          cursor = db.query(
              TABLE_NAME,
              new String[] {
                  "COUNT(*)"
              },
              selection,
              new String[] {
                  messageId.getTimetamp()+""
              },
              null,
              null,
              null
          );
          Log.w(TAG, "cursor created", new Exception());
          if (cursor != null && cursor.moveToFirst()) {
              Log.w(TAG, "Count: " + cursor.getInt(0), new Exception());
              return cursor.getInt(0);
          }
      } finally {
          if (cursor != null)
              Log.w(TAG, "cursor != null. closing", new Exception());
              cursor.close();
      }
      return 0;
  }

  public void deleteThreads(Set<Long> threadIds){
    for(long thread : threadIds){
      deleteThread(thread);
    }
  }

  public void deleteThread(long threadId){
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(
      TABLE_NAME,
      MESSAGE + " IN " +
        "(" +
          " SELECT " + SmsDatabase.DATE_SENT +
          " FROM " + SmsDatabase.TABLE_NAME +
          " WHERE " + SmsDatabase.THREAD_ID +
          " = ?" +
        ")",
      new String[] {
        threadId+""
      });
    notifyConversationListListeners();
  }

  public void deleteReceipts(String address, long timestamp){
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    String condition = MESSAGE + " = ?";
    if(address != "*"){
      condition += " AND " + ADDRESS + " = " + address;
    }
    db.delete(
      TABLE_NAME,
      condition,
      new String[]{
        timestamp + ""
      });
    notifyConversationListListeners();
  }

  public void deleteReceipts(long timestamp) {
    deleteReceipts("*", timestamp);
  }

  public void deleteReceipts(SyncMessageId messageId) {
    deleteReceipts("*", messageId.getTimetamp());
  }

  public void createReceipts(Recipients addresses, long timeStamp){
    for(Recipient address : addresses.getRecipientsList()){
      createReceipts(new SyncMessageId(
          address.getNumber(),
          timeStamp
        )
      );
    }
  }

  public void createReceipts(SyncMessageId messageId){
    ContentValues values = new ContentValues(5);
    values.put(ADDRESS, messageId.getAddress());
    values.put(MESSAGE, messageId.getTimetamp());
    values.put(DATE_SENT, messageId.getTimetamp());
    values.put(DATE_RECEIVED, -1);
    values.put(DATE_READ, -1);
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.insert(TABLE_NAME, null, values);
  }

  public Cursor getReceiptsById(long timestamp, String address) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.query(
      TABLE_NAME,
      MESSAGE_PROJECTION,
      MESSAGE + " = ?" +
      " AND " +
      ADDRESS + " = ?",
      new String[]{
        timestamp+"",
        address
      },
      null,
      null,
      null);
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public class Reader {

    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public ReceiptsRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public int getCount() {
      if (cursor == null) return 0;
      else                return cursor.getCount();
    }

    public ReceiptsRecord getCurrent() {
      long id = cursor.getLong(cursor.getColumnIndexOrThrow(ReceiptDatabase.ID));
      Log.w(TAG, "id: " + id);
      long messageId = cursor.getLong(cursor.getColumnIndexOrThrow(ReceiptDatabase.MESSAGE));
      Log.w(TAG, "messageId: " + messageId);
      long dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(ReceiptDatabase.DATE_SENT));
      Log.w(TAG, "dateSent: " + dateSent);
      long dateReceived = cursor.getLong(cursor.getColumnIndexOrThrow(ReceiptDatabase.DATE_RECEIVED));
      Log.w(TAG, "dateReceived: " + dateReceived);
      long dateRead = cursor.getLong(cursor.getColumnIndexOrThrow(ReceiptDatabase.DATE_READ));
      Log.w(TAG, "dateRead: " + dateSent);
      return new ReceiptsRecord(context, id, messageId, dateSent, dateReceived, dateRead);
    }

    public void close() {
      cursor.close();
    }
  }
}