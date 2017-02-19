package com.medxnote.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import com.medxnote.securesms.crypto.IdentityKeyParcelable;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.IdentityDatabase;
import com.medxnote.securesms.database.MmsAddressDatabase;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.database.MmsSmsDatabase;
import com.medxnote.securesms.database.PushDatabase;
import com.medxnote.securesms.database.ReceiptDatabase;
import com.medxnote.securesms.database.SmsDatabase;
import com.medxnote.securesms.database.documents.IdentityKeyMismatch;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.jobs.PushDecryptJob;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.sms.MessageSender;
import com.medxnote.securesms.util.Base64;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;

public class ReceiptDialog extends AlertDialog {

  private static final String TAG = ConfirmIdentityDialog.class.getSimpleName();

  private OnClickListener callback;

  public ReceiptDialog(Context context)
  {
    super(context);
    String          introduction    = String.format("blablabla");
    SpannableString spannableString = new SpannableString(introduction);
    setTitle("Yo");
    setMessage(spannableString);
    setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.ConfirmIdentityDialog_accept), new AcceptListener());
  }

  @Override
  public void show() {
    super.show();
    ((TextView)this.findViewById(android.R.id.message))
                   .setMovementMethod(LinkMovementMethod.getInstance());
  }

  public void setCallback(OnClickListener callback) {
    this.callback = callback;
  }

  private class AcceptListener implements OnClickListener {
    private AcceptListener(){
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (callback != null) callback.onClick(null, 0);
    }
  }
}