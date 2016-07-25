/**
 * Copyright (C) 2014 Open Whisper Systems
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
package com.medxnote.securesms;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.components.AvatarImageView;
import com.medxnote.securesms.components.FromTextView;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.database.MmsSmsDatabase;
import com.medxnote.securesms.database.documents.IdentityKeyMismatch;
import com.medxnote.securesms.database.documents.NetworkFailure;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.sms.MessageSender;

/**
 * A simple view to show the recipients of a message
 *
 * @author Jake McGinty
 */
public class MessageRecipientListItem extends RelativeLayout
    implements Recipient.RecipientModifiedListener
{
  private final static String TAG = MessageRecipientListItem.class.getSimpleName();

  private Recipient       recipient;
  private FromTextView    fromView;
  private TextView        errorDescription;
  private Button          conflictButton;
  private Button          resendButton;
  private Button          infoButton;
  private AvatarImageView contactPhotoImage;

  private final Handler handler = new Handler();

  public MessageRecipientListItem(Context context) {
    super(context);
  }

  public MessageRecipientListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    this.fromView          = (FromTextView)    findViewById(R.id.from);
    this.errorDescription  = (TextView)        findViewById(R.id.error_description);
    this.contactPhotoImage = (AvatarImageView) findViewById(R.id.contact_photo_image);
    this.conflictButton    = (Button)          findViewById(R.id.conflict_button);
    this.resendButton      = (Button)          findViewById(R.id.resend_button);
    this.infoButton        = (Button)          findViewById(R.id.info_button);
  }

  public void set(final MasterSecret masterSecret,
                  final MessageRecord record,
                  final Recipient recipient,
                  final boolean isPushGroup,
                  final boolean showInfo)
  {
    this.recipient = recipient;

    recipient.addListener(this);
    fromView.setText(recipient);
    contactPhotoImage.setAvatar(recipient, false);
    setIssueIndicators(masterSecret, record, isPushGroup, recipient, showInfo);
  }

  private void setIssueIndicators(
    final MasterSecret masterSecret,
    final MessageRecord record,
    final boolean isPushGroup,
    final Recipient recipient
  ){
    setIssueIndicators(masterSecret, record, isPushGroup, recipient, false);
  }

  private void setIssueIndicators(final MasterSecret masterSecret,
                                  final MessageRecord record,
                                  final boolean isPushGroup,
                                  final Recipient recipient,
                                  final boolean showInfo)
  {
    final NetworkFailure      networkFailure = getNetworkFailure(record);
    final IdentityKeyMismatch keyMismatch    = networkFailure == null ? getKeyMismatch(record) : null;

    String errorText = "";

    if (keyMismatch != null) {
      resendButton.setVisibility(View.GONE);
      conflictButton.setVisibility(View.VISIBLE);
      infoButton.setVisibility(View.GONE);

      errorText = getContext().getString(R.string.MessageDetailsRecipient_new_identity);
      conflictButton.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          new ConfirmIdentityDialog(getContext(), masterSecret, record, keyMismatch).show();
        }
      });
    } else if (networkFailure != null || (!isPushGroup && record.isFailed())) {
      resendButton.setVisibility(View.VISIBLE);
      resendButton.setEnabled(true);
      resendButton.requestFocus();
      conflictButton.setVisibility(View.GONE);
      infoButton.setVisibility(View.GONE);

      errorText = getContext().getString(R.string.MessageDetailsRecipient_failed_to_send);
      resendButton.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          resendButton.setEnabled(false);
          new ResendAsyncTask(masterSecret, record, networkFailure).execute();
        }
      });
    } else {
      resendButton.setVisibility(View.GONE);
      conflictButton.setVisibility(View.GONE);

      final Recipients intermediaryRecipients;
      if (record.isMms()) {
        intermediaryRecipients = DatabaseFactory.getMmsAddressDatabase(
                getContext()
        ).getRecipientsForId(record.getId());
      } else {
        intermediaryRecipients = record.getRecipients();
      }
      Log.e(TAG, "simple name" + getContext().getClass().getSimpleName(), new Exception());
      if (intermediaryRecipients.isGroupRecipient() && showInfo){
        final MessageRecord messageRecord = record;
        infoButton.setEnabled(true);
        infoButton.setVisibility(View.VISIBLE);
        infoButton.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            //new ReceiptDialog(getContext()).show();
            Intent intent = new Intent(getContext(), MessageGroupDetailsActivity.class);
            intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
            intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, messageRecord.getId());
            intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, messageRecord.getThreadId());
            intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
            intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, isPushGroup && messageRecord.isPush());
            intent.putExtra(MessageDetailsActivity.RECIPIENTS_IDS_EXTRA, new long[]{recipient.getRecipientId()});
            getContext().startActivity(intent);
          }
        });
      }
    }

    errorDescription.setText(errorText);
    errorDescription.setVisibility(TextUtils.isEmpty(errorText) ? View.GONE : View.VISIBLE);
  }

  private NetworkFailure getNetworkFailure(final MessageRecord record) {
    if (record.hasNetworkFailures()) {
      for (final NetworkFailure failure : record.getNetworkFailures()) {
        if (failure.getRecipientId() == recipient.getRecipientId()) {
          return failure;
        }
      }
    }
    return null;
  }

  private IdentityKeyMismatch getKeyMismatch(final MessageRecord record) {
    if (record.isIdentityMismatchFailure()) {
      for (final IdentityKeyMismatch mismatch : record.getIdentityKeyMismatches()) {
        if (mismatch.getRecipientId() == recipient.getRecipientId()) {
          return mismatch;
        }
      }
    }
    return null;
  }

  public void unbind() {
    if (this.recipient != null) this.recipient.removeListener(this);
  }

  @Override
  public void onModified(final Recipient recipient) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(recipient);
        contactPhotoImage.setAvatar(recipient, false);
      }
    });
  }

  private class ResendAsyncTask extends AsyncTask<Void,Void,Void> {
    private final MasterSecret   masterSecret;
    private final MessageRecord  record;
    private final NetworkFailure failure;

    public ResendAsyncTask(MasterSecret masterSecret, MessageRecord record, NetworkFailure failure) {
      this.masterSecret = masterSecret;
      this.record       = record;
      this.failure      = failure;
    }

    @Override
    protected Void doInBackground(Void... params) {
      MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(getContext());
      mmsDatabase.removeFailure(record.getId(), failure);

      if (record.getRecipients().isGroupRecipient()) {
        MessageSender.resendGroupMessage(getContext(), masterSecret, record, failure.getRecipientId());
      } else {
        MessageSender.resend(getContext(), masterSecret, record);
      }
      return null;
    }
  }

}
