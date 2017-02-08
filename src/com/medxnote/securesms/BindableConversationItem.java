package com.medxnote.securesms;

import android.support.annotation.NonNull;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.recipients.Recipients;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MasterSecret masterSecret,
            @NonNull MessageRecord messageRecord,
            @NonNull Locale locale,
            @NonNull Set<MessageRecord> batchSelected,
            @NonNull Recipients recipients);
}
