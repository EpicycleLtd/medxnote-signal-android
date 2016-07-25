/**
 * Copyright (C) 2012 Moxie Marlinspike
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

package com.medxnote.securesms.database.model;

import android.content.Context;
import android.text.SpannableString;

import com.medxnote.securesms.R;
import com.medxnote.securesms.database.MmsSmsColumns;
import com.medxnote.securesms.database.SmsDatabase;
import com.medxnote.securesms.database.documents.IdentityKeyMismatch;
import com.medxnote.securesms.database.documents.NetworkFailure;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.Recipients;

import java.util.LinkedList;
import java.util.List;

/**
 * The message record model which represents receipts of SMS messages.
 *
 * @author Alexandr Barabash
 *
 */

public class ReceiptsRecord {

  private long id;
  private long messageId;
  private long dateSend;
  private long dateReceived;
  private long dateRead;

  public ReceiptsRecord(Context context,
                        long id,
                        long messageId,
                        long dateSent,
                        long dateReceived,
                        long dateRead){
    this.id           = id;
    this.messageId    = messageId;
    this.dateSend     = dateSent;
    this.dateReceived = dateReceived;
    this.dateRead     = dateRead;
  }

  public boolean hasSend(){
    return dateSend > 0;
  }

  public boolean hasReceived(){
    return dateReceived > 0;
  }

  public boolean hasRead(){
    return dateRead > 0;
  }

  public long getDateSent(){
    return dateSend;
  }

  public long getDateReceived(){
    return dateReceived;
  }

  public long getDateRead(){
    return dateRead;
  }
}