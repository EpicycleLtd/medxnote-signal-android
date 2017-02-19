package com.medxnote.securesms;

import android.support.annotation.NonNull;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.model.ThreadRecord;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode);
}
