package com.medxnote.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.util.AbstractCursorLoader;

public class ConversationLoader extends AbstractCursorLoader {
  private final long threadId;
  private       long limit;
  private       boolean firstLoad;

  public ConversationLoader(Context context, long threadId, long limit) {
    super(context);
    this.threadId = threadId;
    this.limit  = limit;
    this.firstLoad = true;
  }

  public boolean hasLimit() {
    return limit > 0;
  }

  @Override
  public Cursor getCursor() {
    if (firstLoad) {
      return DatabaseFactory.getMmsSmsDatabase(context).getConversationMessages(threadId, limit);
    } else {
      return DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId, limit);
    }

  }

  public boolean isFirstLoad() {
    return firstLoad;
  }

  public void setFirstLoad(boolean firstLoad) {
    this.firstLoad = firstLoad;
  }
}
