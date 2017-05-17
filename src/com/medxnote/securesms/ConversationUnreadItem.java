package com.medxnote.securesms;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.Recipients;

import java.util.Locale;
import java.util.Set;

/**
 * Created by jnovkovic on 4/28/17.
 */

public class ConversationUnreadItem extends LinearLayout implements Recipients.RecipientsModifiedListener, Recipient.RecipientModifiedListener, BindableConversationItem, View.OnClickListener {

    private static final String TAG = ConversationUnreadItem.class.getSimpleName();

    private TextView statusMsg;

    public ConversationUnreadItem(Context context) {
        super(context);
    }

    public ConversationUnreadItem(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.statusMsg = (TextView) findViewById(R.id.conversation_unread_message);
    }

    @Override
    public void onModified(Recipient recipient) {

    }

    @Override
    public void onClick(View v) {

    }

    @Override
    public void unbind() {

    }

    @Override
    public void bind(@NonNull MasterSecret masterSecret, @NonNull MessageRecord messageRecord, @NonNull Locale locale, @NonNull Set<MessageRecord> batchSelected, @NonNull Recipients recipients) {

    }

    public void bindMessage(String message) {
        statusMsg.setText(message);
    }



    @Override
    public void onModified(Recipients recipient) {

    }
}
