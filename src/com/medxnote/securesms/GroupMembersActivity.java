package com.medxnote.securesms;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;

import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.loaders.GroupMembersLoader;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.util.GroupUtil;

import java.io.IOException;

public class GroupMembersActivity extends BaseActionBarActivity
        implements LoaderManager.LoaderCallbacks<Recipients> {

    private static final String TAG = GroupMembersActivity.class.getSimpleName();

    private byte[] groupId;
    private RecyclerView mMembersRecyclerView;
    private GroupMembersAdapter mGroupMembersAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_members_activity);
        initializeResources();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.GroupMembersActivity__group_members);
        }

        initializeLoader(GroupMembersLoader.ID);
    }

    @Override
    public Loader<Recipients> onCreateLoader(int id, Bundle args) {
        return new GroupMembersLoader(this, groupId);
    }

    @Override
    public void onLoadFinished(Loader<Recipients> loader,
                               Recipients recipients) {
        int id = loader.getId();
        if (id == GroupMembersLoader.ID) {
            handleGroupRecord(recipients);
        } else {
            throw new AssertionError("Unknown loader. ");
        }
    }

    @Override
    public void onLoaderReset(Loader<Recipients> loader) {
        if (mGroupMembersAdapter != null) {
            mGroupMembersAdapter.swap(null);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return false;
    }

    private void handleGroupRecord(final Recipients recipients) {

        new AsyncTask<Void, Void, Recipient>() {

            @Override
            protected Recipient doInBackground(Void... voids) {
                String adminNumber = DatabaseFactory.getGroupDatabase(GroupMembersActivity.this).getAdmin(groupId);

                if (adminNumber != null) {
                    return RecipientFactory.getRecipientsFromString(GroupMembersActivity.this, adminNumber, false)
                            .getPrimaryRecipient();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Recipient adminRecipient) {
                super.onPostExecute(adminRecipient);

                mGroupMembersAdapter = new GroupMembersAdapter(GroupMembersActivity.this,
                        recipients.getRecipientsList(), adminRecipient);

                mMembersRecyclerView.setAdapter(mGroupMembersAdapter);
            }

        }.execute();

    }

    private void initializeLoader(int id) {
        getSupportLoaderManager().initLoader(id, null, this).forceLoad();
    }

    private void initializeResources() {
        try {
            groupId = GroupUtil.getDecodedId(getIntent().getStringExtra("groupId"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMembersRecyclerView = (RecyclerView) findViewById(R.id.group_members_list);
            mMembersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            mMembersRecyclerView.setHasFixedSize(true);

        mGroupMembersAdapter = new GroupMembersAdapter(this, null, null);
    }
}
