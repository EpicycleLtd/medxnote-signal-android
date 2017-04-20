package com.medxnote.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getRecipientPreferenceDatabase(getContext())
                          .getBlocked();
  }

}
