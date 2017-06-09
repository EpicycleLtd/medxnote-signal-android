package com.medxnote.securesms;


import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.medxnote.securesms.crypto.MasterCipher;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.CursorRecyclerViewAdapter;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.EditDatabase;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.database.model.SmsMessageRecord;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


public class EditMessageAdapter extends CursorRecyclerViewAdapter<EditMessageAdapter.ViewHolder> {

    private LayoutInflater mLayoutInflater;
    private EditDatabase mEditDatabase;
    private MasterSecret mMasterSecret;
    private Locale mLocale;
    private MasterCipher mMasterCipher;
    private final Set<MessageRecord> batchSet  = Collections.synchronizedSet(new HashSet<MessageRecord>());

    static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View itemView) {
            super(itemView);
        }

        public ConversationItem getItem() {
            return (ConversationItem) itemView;
        }
    }

    public EditMessageAdapter(Context context, Cursor cursor, MasterSecret masterSecret, Locale locale) {
        super(context, cursor);
        mLayoutInflater = LayoutInflater.from(context);
        mEditDatabase = DatabaseFactory.getEditDatabase(context);
        mMasterSecret = masterSecret;
        mMasterCipher = new MasterCipher(masterSecret);
        mLocale = locale;
    }

    @Override
    public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
        ConversationItem conversationItem = (ConversationItem) mLayoutInflater.inflate(R.layout.conversation_item_sent, parent, false);
        return new ViewHolder(conversationItem);
    }

    @Override
    public void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
        SmsMessageRecord smsMessageRecord = getSmsMessageRecord(cursor);
        viewHolder.getItem().bind(mMasterSecret, smsMessageRecord, mLocale, batchSet, smsMessageRecord.getRecipients());
    }

    @Override
    public void onItemViewRecycled(ViewHolder holder) {
        holder.getItem().unbind();
    }

    private SmsMessageRecord getSmsMessageRecord(Cursor cursor) {
        return mEditDatabase.readerFor(cursor, mMasterCipher).getCurrent();
    }

}
