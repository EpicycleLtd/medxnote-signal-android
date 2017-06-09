package com.medxnote.securesms;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.medxnote.securesms.color.MaterialColor;
import com.medxnote.securesms.components.ComposeText;
import com.medxnote.securesms.components.SendButton;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.crypto.MasterSecretUnion;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.EncryptingSmsDatabase;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.database.MmsSmsDatabase;
import com.medxnote.securesms.database.SmsDatabase;
import com.medxnote.securesms.database.loaders.MessageDetailsLoader;
import com.medxnote.securesms.database.loaders.MessageEditHistoryLoader;
import com.medxnote.securesms.database.model.MessageRecord;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.util.DynamicLanguage;
import com.medxnote.securesms.util.DynamicTheme;
import com.medxnote.securesms.util.ViewUtil;

import org.whispersystems.libsignal.InvalidMessageException;

public class MessageEditActivity extends PassphraseRequiredActionBarActivity
        implements LoaderCallbacks<Cursor>, View.OnClickListener {

    private static final String TAG = MessageEditActivity.class.getSimpleName();

    public static final String MESSAGE_ID_EXTRA = "message_id";
    public static final String MESSAGE_TRANSPORT_EXTRA = "message_transport";
    public static final String THREAD_ID_EXTRA = "thread_id";
    public static final String MESSAGE_OUTGOING_EXTRA = "message_outgoing";

    public static final int MESSAGE_DETAILS_LOADER = 0;
    public static final int MESSAGE_EDIT_HISTORY_LOADER = 1;

    private DynamicTheme mDynamicTheme = new DynamicTheme();
    private DynamicLanguage mDynamicLanguage = new DynamicLanguage();

    private MasterSecret mMasterSecret;
    private MasterSecretUnion mMasterSecretUnion;

    private String mMessageTransport;
    private long mMessageId;
    private long threadId;
    private RecyclerView mRecyclerView;
    private ComposeText mComposeText;
    private SendButton mSendButton;
    private MessageRecord mOldMessageRecord;
    private ProgressBar mProgressBar;
    private TextView mEmptyHistory;

    @Override
    protected void onPreCreate() {
        super.onPreCreate();

        mDynamicTheme.onCreate(this);
        mDynamicLanguage.onCreate(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState, @NonNull MasterSecret mMasterSecret) {
        super.onCreate(savedInstanceState, mMasterSecret);
        setContentView(R.layout.message_edit_activity);
        this.mMasterSecret = mMasterSecret;

        setActionBar();
        initializeResources();

        getSupportLoaderManager().initLoader(MESSAGE_DETAILS_LOADER, Bundle.EMPTY, this).forceLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mDynamicTheme.onResume(this);
        mDynamicLanguage.onResume(this);

        mSendButton.setDefaultTransport(TransportOption.Type.TEXTSECURE);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        mProgressBar.setVisibility(View.VISIBLE);
        if (id == MESSAGE_DETAILS_LOADER) {
            return new MessageDetailsLoader(this, mMessageTransport, mMessageId);
        }
        return new MessageEditHistoryLoader(this, mMessageTransport, mMessageId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            int id = loader.getId();
            if (id == MESSAGE_DETAILS_LOADER) {
                if (data != null && data.getCount() != 0) {
                    mOldMessageRecord = getMessageRecord(this, data, mMessageTransport);
                    setComposeText();
                    getSupportLoaderManager().initLoader(MESSAGE_EDIT_HISTORY_LOADER, Bundle.EMPTY, this).forceLoad();
                }
            } else  if (id == MESSAGE_EDIT_HISTORY_LOADER) {
                if (data != null && data.getCount() != 0) {
                    mRecyclerView.setAdapter(new EditMessageAdapter(this, data, mMasterSecret, mDynamicLanguage.getCurrentLocale()));
                    scrollToBottom();
                    setVisibility(true);
                } else {
                    setVisibility(false);
                }
            } else {
                throw new AssertionError("Unknown loader.");
            }
    }

    private void setActionBarColor(MaterialColor color) {
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));
        setStatusBarColor(color.toStatusBarColor(this));
    }

    private void setVisibility(boolean hasData) {
        mProgressBar.setVisibility(View.GONE);
        mRecyclerView.setVisibility(hasData ? View.VISIBLE : View.GONE);
        mEmptyHistory.setVisibility(hasData ? View.GONE : View.VISIBLE);
    }

    private void setComposeText() {
        String message = mOldMessageRecord.getBody().getBody();
            mComposeText.setText(message);
            mComposeText.setSelection(message.length());
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (getAdapter() != null) {
            getAdapter().changeCursor(null);
        }
    }

    private EditMessageAdapter getAdapter() {
        return (EditMessageAdapter) mRecyclerView.getAdapter();
    }

    public void scrollToBottom() {
        mRecyclerView.getItemAnimator().isRunning(new RecyclerView.ItemAnimator.ItemAnimatorFinishedListener() {
            @Override
            public void onAnimationsFinished() {
                mRecyclerView.stopScroll();
                mRecyclerView.smoothScrollToPosition(0);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return false;
    }

    private String getEditedMessage() throws InvalidMessageException {
        String rawText = mComposeText.getText().toString();
        if (rawText.length() < 1) {
            Toast.makeText(this, getString(R.string.ConversationActivity_message_is_empty_exclamation), Toast.LENGTH_SHORT).show();
            throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));
        }
        return rawText;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.send_button:
                saveNewMessage();
                break;
            default:
                break;
        }
    }

    private void saveNewMessage() {
        String oldBody = mOldMessageRecord.getBody().getBody();
        String newBody = mComposeText.getText().toString();
        try {
            if (!newBody.equals(oldBody)) {
                updateMessage();
                Intent intent = new Intent();
                    intent.putExtra(MESSAGE_ID_EXTRA, mMessageId);
                    intent.putExtra(MESSAGE_TRANSPORT_EXTRA, mOldMessageRecord.isMms() ?
                            MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
                setResult(RESULT_OK, intent);
            }
            finish();
        } catch (InvalidMessageException e) {
            e.printStackTrace();
        }
    }

    private void updateMessage() throws InvalidMessageException {

        String newBody = getEditedMessage();

        if (mOldMessageRecord.isMms()) {
            DatabaseFactory.getMmsDatabase(this).updateMessageBody(mMasterSecretUnion, mMessageId, threadId, newBody);
        } else {
            DatabaseFactory.getEncryptingSmsDatabase(this).updateMessageBody(mMasterSecretUnion, mMessageId, threadId, newBody);
        }

        DatabaseFactory.getEditDatabase(this).insert(mMasterSecretUnion, mOldMessageRecord);
    }

    private void initializeResources() {
        mMasterSecretUnion = new MasterSecretUnion(mMasterSecret);
        mMessageTransport = getIntent().getStringExtra(MESSAGE_TRANSPORT_EXTRA);
        mMessageId = getIntent().getLongExtra(MESSAGE_ID_EXTRA, -1);
        threadId = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
        boolean isOutgoing = getIntent().getBooleanExtra(MESSAGE_OUTGOING_EXTRA, false);

        Recipients recipients = RecipientFactory.getRecipientsForIds(this, getIntent().getLongArrayExtra(ConversationActivity.RECIPIENTS_EXTRA), true);
        setActionBarColor(recipients.getColor());

        mProgressBar = ViewUtil.findById(this, R.id.progress_bar);
            mProgressBar.getIndeterminateDrawable().setColorFilter(
                    getResources().getColor(R.color.textsecure_primary), Mode.SRC_IN);

        mSendButton = ViewUtil.findById(this, R.id.send_button);
            mSendButton.setOnClickListener(this);
            mSendButton.setColorFilter(getResources().getColor(R.color.textsecure_primary), Mode.MULTIPLY);

        mComposeText = ViewUtil.findById(this, R.id.embedded_text_editor);

        if (!isOutgoing) {
            ViewUtil.findById(this, R.id.compose_bubble).setVisibility(View.GONE);
        }

        mRecyclerView = ViewUtil.findById(this, R.id.recycler_view);
            mRecyclerView.setHasFixedSize(true);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mEmptyHistory = ViewUtil.findById(this, R.id.edit_history_is_empty);
    }

    private MessageRecord getMessageRecord(Context context, Cursor cursor, String type) {
        switch (type) {
            case MmsSmsDatabase.SMS_TRANSPORT:
                EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
                SmsDatabase.Reader reader = smsDatabase.readerFor(mMasterSecret, cursor);
                return reader.getNext();
            case MmsSmsDatabase.MMS_TRANSPORT:
                MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
                MmsDatabase.Reader mmsReader = mmsDatabase.readerFor(mMasterSecret, cursor);
                return mmsReader.getNext();
            default:
                throw new AssertionError("no valid message type specified");
        }
    }

    private void setActionBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.MessageEditActivity__edit_message);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

}
