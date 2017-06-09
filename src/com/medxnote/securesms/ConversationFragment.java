/**
 * Copyright (C) 2015 Open Whisper Systems
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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemAnimator.ItemAnimatorFinishedListener;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;

import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.MmsSmsDatabase;
import com.medxnote.securesms.database.model.MediaMmsMessageRecord;
import com.medxnote.securesms.mms.Slide;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.util.ViewUtil;
import com.medxnote.securesms.util.task.ProgressDialogAsyncTask;
import com.medxnote.securesms.database.loaders.ConversationLoader;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.sms.MessageSender;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConversationFragment extends Fragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = ConversationFragment.class.getSimpleName();

  private static final long   PARTIAL_CONVERSATION_LIMIT = 500L;

  private final ActionModeCallback actionModeCallback     = new ActionModeCallback();
  private final ConversationAdapter.ItemClickListener selectionClickListener = new ConversationFragmentItemClickListener();

  private ConversationFragmentListener listener;

  private ConversationAdapter mConversationAdapter;
  private MasterSecret masterSecret;
  private Recipients recipients;
  private long         threadId;
  private ActionMode   actionMode;
  private Locale       locale;
  private RecyclerView list;
  private View         loadMoreView;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.masterSecret = getArguments().getParcelable("master_secret");
    this.locale       = (Locale) getArguments().getSerializable(PassphraseRequiredActionBarActivity.LOCALE_EXTRA);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_fragment, container, false);
    list = ViewUtil.findById(view, android.R.id.list);
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, true);
    list.setHasFixedSize(false);
    list.setLayoutManager(layoutManager);

    loadMoreView = inflater.inflate(R.layout.load_more_header, container, false);
    loadMoreView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Bundle args = new Bundle();
        args.putLong("limit", 0);
        getLoaderManager().restartLoader(0, args, ConversationFragment.this);
      }
    });
    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    initializeResources();
    initializeListAdapter();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.listener = (ConversationFragmentListener)activity;
  }

  @Override
  public void onResume() {
    super.onResume();

    if (mConversationAdapter != null) {
      mConversationAdapter.notifyDataSetChanged();
    }
  }

  public void onNewIntent() {
    if (actionMode != null) {
      actionMode.finish();
    }

    initializeResources();
    initializeListAdapter();

    if (threadId == -1) {
      getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
    }
  }

  public void reloadList() {
    getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
  }

  private void initializeResources() {
    this.recipients = RecipientFactory.getRecipientsForIds(getActivity(), getActivity().getIntent().getLongArrayExtra("recipients"), true);
    this.threadId   = this.getActivity().getIntent().getLongExtra("thread_id", -1);
  }

  private void initializeListAdapter() {
    if (this.recipients != null && this.threadId != -1) {
      mConversationAdapter = new ConversationAdapter(getActivity(), masterSecret, locale, selectionClickListener, null, this.recipients);
      list.setAdapter(mConversationAdapter);
      getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
      list.getItemAnimator().setSupportsChangeAnimations(false);
      list.getItemAnimator().setMoveDuration(120);
    }
  }

  private void setCorrectMenuVisibility(Menu menu) {
    Set<MessageRecord> messageRecords = mConversationAdapter.getSelectedItems();

    if (actionMode != null && messageRecords.size() == 0) {
      actionMode.finish();
      return;
    }

    int selectedItems = mConversationAdapter.getSelectedItems().size();
    if (actionMode != null && selectedItems != 0) {
      actionMode.setTitle(getString(R.string.ConversationFragment__selected) + "(" + selectedItems + ")");
    }

    if (messageRecords.size() > 1) {
      menu.findItem(R.id.menu_context_edit_message).setVisible(false);
      menu.findItem(R.id.menu_context_forward).setVisible(false);
      menu.findItem(R.id.menu_context_details).setVisible(false);
//      menu.findItem(R.id.menu_context_save_attachment).setVisible(false);
      menu.findItem(R.id.menu_context_resend).setVisible(false);
    } else {
      MessageRecord messageRecord = getSelectedMessageRecord();

      menu.findItem(R.id.menu_context_resend).setVisible(messageRecord.isFailed());
//      menu.findItem(R.id.menu_context_save_attachment).setVisible(messageRecord.isMms()              &&
//                                                                  !messageRecord.isMmsNotification() &&
//                                                                  ((MediaMmsMessageRecord)messageRecord).containsMediaSlide());

      menu.findItem(R.id.menu_context_forward).setVisible(true);
      menu.findItem(R.id.menu_context_details).setVisible(true);
      menu.findItem(R.id.menu_context_copy).setVisible(true);
      menu.findItem(R.id.menu_context_edit_message).setVisible(true);
    }
  }

  private MessageRecord getSelectedMessageRecord() {
    Set<MessageRecord> messageRecords = mConversationAdapter.getSelectedItems();

    if (messageRecords.size() == 1) return messageRecords.iterator().next();
    else                            throw new AssertionError();
  }

  public void reload(Recipients recipients, long threadId) {
    this.recipients = recipients;

    if (this.threadId != threadId) {
      this.threadId = threadId;
      initializeListAdapter();
    }
  }

  public void scrollToBottom() {
    list.getItemAnimator().isRunning(new ItemAnimatorFinishedListener() {
      @Override
      public void onAnimationsFinished() {
        list.stopScroll();
        list.smoothScrollToPosition(0);
      }
    });
  }

  private void handleCopyMessage(final Set<MessageRecord> messageRecords) {
    List<MessageRecord> messageList = new LinkedList<>(messageRecords);
    Collections.sort(messageList, new Comparator<MessageRecord>() {
      @Override
      public int compare(MessageRecord lhs, MessageRecord rhs) {
        if      (lhs.getDateReceived() < rhs.getDateReceived())  return -1;
        else if (lhs.getDateReceived() == rhs.getDateReceived()) return 0;
        else                                                     return 1;
      }
    });

    StringBuilder    bodyBuilder = new StringBuilder();
    ClipboardManager clipboard   = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
    boolean          first       = true;

    for (MessageRecord messageRecord : messageList) {
      String body = messageRecord.getDisplayBody().toString();

      if (body != null) {
        if (!first) bodyBuilder.append('\n');
        bodyBuilder.append(body);
        first = false;
      }
    }

    String result = bodyBuilder.toString();

    if (!TextUtils.isEmpty(result))
        clipboard.setText(result);
  }

  private void handleEditMessage(MessageRecord message) {
    Intent intent = new Intent(getActivity(), MessageEditActivity.class);
      intent.putExtra(MessageEditActivity.MESSAGE_ID_EXTRA, message.getId());
      intent.putExtra(MessageEditActivity.MESSAGE_TRANSPORT_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
      intent.putExtra(MessageEditActivity.THREAD_ID_EXTRA, threadId);
      intent.putExtra(MessageEditActivity.MESSAGE_OUTGOING_EXTRA, message.isOutgoing());
      intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    startActivityForResult(intent, 1);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == 1) {
      if (resultCode == Activity.RESULT_OK) {
        handleSendEditedMessage(data.getLongExtra(MessageEditActivity.MESSAGE_ID_EXTRA, -1),
                data.getStringExtra(MessageEditActivity.MESSAGE_TRANSPORT_EXTRA));
      }
    }
  }

  private void handleSendEditedMessage(long messageId, final String type) {
    final Context context = getActivity().getApplicationContext();
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... id) {
        long messageId = id[0];
        MessageRecord messageRecord;
        switch (type) {
          case MmsSmsDatabase.SMS_TRANSPORT:
            messageRecord = DatabaseFactory.getEncryptingSmsDatabase(context).readerFor(
                    DatabaseFactory.getEncryptingSmsDatabase(context).getMessage(messageId)).getNext();
            break;
          case MmsSmsDatabase.MMS_TRANSPORT:
            messageRecord = DatabaseFactory.getMmsDatabase(context).readerFor(masterSecret,
                    DatabaseFactory.getMmsDatabase(context).getMessage(messageId)).getNext();
            break;
          default:
            throw new AssertionError("Unknown transport type.");
        }
        MessageSender.sendEditedMessage(context, messageRecord);
        return null;
      }
    }.execute(messageId);
  }

  private void handleDeleteMessages(final Set<MessageRecord> messageRecords) {
    int                 messagesCount = messageRecords.size();
    AlertDialog.Builder builder       = new AlertDialog.Builder(getActivity());

    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messagesCount, messagesCount));
    builder.setMessage(getActivity().getResources().getQuantityString(R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages, messagesCount, messagesCount));
    builder.setCancelable(true);

    builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        new ProgressDialogAsyncTask<MessageRecord, Void, Void>(getActivity(),
                                                               R.string.ConversationFragment_deleting,
                                                               R.string.ConversationFragment_deleting_messages)
        {
          @Override
          protected Void doInBackground(MessageRecord... messageRecords) {
            for (MessageRecord messageRecord : messageRecords) {
              boolean threadDeleted;

              if (messageRecord.isMms()) {
                threadDeleted = DatabaseFactory.getMmsDatabase(getActivity()).delete(messageRecord.getId());
              } else {
                threadDeleted = DatabaseFactory.getSmsDatabase(getActivity()).deleteMessage(messageRecord.getId());
              }
              DatabaseFactory.getReceiptDatabase(getActivity()).deleteReceipts(messageRecord.getTimestamp());

              if (threadDeleted) {
                threadId = -1;
                listener.setThreadId(threadId);
              }
            }

            return null;
          }
        }.execute(messageRecords.toArray(new MessageRecord[messageRecords.size()]));
      }
    });

    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleDisplayDetails(MessageRecord message) {
    Intent intent = new Intent(getActivity(), MessageDetailsActivity.class);
      intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
      intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, message.getId());
      intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, threadId);
      intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
      intent.putExtra(MessageDetailsActivity.RECIPIENTS_IDS_EXTRA, recipients.getIds());
    startActivity(intent);
  }

  private void handleForwardMessage(MessageRecord message) {
    Intent composeIntent = new Intent(getActivity(), ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_TEXT, message.getDisplayBody().toString());
    if (message.isMms()) {
      MediaMmsMessageRecord mediaMessage = (MediaMmsMessageRecord) message;
      if (mediaMessage.containsMediaSlide()) {
        Slide slide = mediaMessage.getSlideDeck().getSlides().get(0);
        composeIntent.putExtra(Intent.EXTRA_STREAM, slide.getUri());
        composeIntent.setType(slide.getContentType());
      }
    }
    startActivity(composeIntent);
  }

  private void handleResendMessage(final MessageRecord message) {
    final Context context = getActivity().getApplicationContext();
    new AsyncTask<MessageRecord, Void, Void>() {
      @Override
      protected Void doInBackground(MessageRecord... messageRecords) {
        MessageRecord messageRecord = messageRecords[0];
        if (messageRecord.getRecipients().isGroupRecipient()) {
          MessageSender.resendGroupMessage(context, masterSecret, messageRecord, messageRecord.getIdentityKeyMismatches().get(0).getRecipientId());
        }
        MessageSender.resend(context, masterSecret, messageRecord);
        return null;
      }
    }.execute(message);
  }
/*
  private void handleSaveAttachment(final MediaMmsMessageRecord message) {
    SaveAttachmentTask.showWarningDialog(getActivity(), new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        for (Slide slide : message.getSlideDeck().getSlides()) {
          if (slide.hasImage() || slide.hasVideo() || slide.hasAudio()) {
            SaveAttachmentTask saveTask = new SaveAttachmentTask(getActivity(), masterSecret);
            saveTask.execute(new Attachment(slide.getUri(), slide.getContentType(), message.getDateReceived()));
            return;
          }
        }

        Log.w(TAG, "No slide with attachable media found, failing nicely.");
        Toast.makeText(getActivity(),
                       getResources().getQuantityString(R.plurals.ConversationFragment_error_while_saving_attachments_to_sd_card, 1),
                       Toast.LENGTH_LONG).show();
      }
    });
  }
*/
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new ConversationLoader(getActivity(), threadId, args.getLong("limit", PARTIAL_CONVERSATION_LIMIT));
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    if (mConversationAdapter != null) {
      if (cursor.getCount() >= PARTIAL_CONVERSATION_LIMIT && ((ConversationLoader)loader).hasLimit()) {
        mConversationAdapter.setFooterView(loadMoreView);
      } else {
        mConversationAdapter.setFooterView(null);
      }
      mConversationAdapter.changeCursor(cursor);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    if (mConversationAdapter != null) {
      mConversationAdapter.changeCursor(null);
    }
  }

  public interface ConversationFragmentListener {
    void setThreadId(long threadId);
  }

  private class ConversationFragmentItemClickListener implements ConversationAdapter.ItemClickListener {

    @Override
    public void onItemClick(ConversationItem item) {
      if (actionMode != null) {
        MessageRecord messageRecord = item.getMessageRecord();
        mConversationAdapter.toggleSelection(messageRecord);
        mConversationAdapter.notifyDataSetChanged();

        setCorrectMenuVisibility(actionMode.getMenu());
      }
    }

    @Override
    public void onItemLongClick(ConversationItem item) {
      if (actionMode == null) {
        mConversationAdapter.toggleSelection(item.getMessageRecord());
        mConversationAdapter.notifyDataSetChanged();

        actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(actionModeCallback);
      }

      setCorrectMenuVisibility(actionMode.getMenu());
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    private int statusBarColor;

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.conversation_context, menu);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Window window = getActivity().getWindow();
        statusBarColor = window.getStatusBarColor();
        window.setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
      }

      setCorrectMenuVisibility(menu);
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      mConversationAdapter.clearSelection();
      mConversationAdapter.notifyDataSetChanged();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        getActivity().getWindow().setStatusBarColor(statusBarColor);
      }

      actionMode = null;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      switch(item.getItemId()) {
        case R.id.menu_context_copy:
          handleCopyMessage(mConversationAdapter.getSelectedItems());
          actionMode.finish();
          return true;
        case R.id.menu_context_delete_message:
          handleDeleteMessages(mConversationAdapter.getSelectedItems());
          actionMode.finish();
          return true;
        case R.id.menu_context_edit_message:
          handleEditMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_details:
          handleDisplayDetails(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_forward:
          handleForwardMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_resend:
          handleResendMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
//        case R.id.menu_context_save_attachment:
//          handleSaveAttachment((MediaMmsMessageRecord)getSelectedMessageRecord());
//          actionMode.finish();
//          return true;
      }

      return false;
    }
  }
}
