package com.medxnote.securesms.database.loaders;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.recipients.Recipients;

public class GroupMembersLoader extends AsyncTaskLoader<Recipients> {

    public static final int ID = 1;

    private byte[] groupId;

    public GroupMembersLoader(Context context, byte[] groupId) {
        super(context);

        this.groupId = groupId;
    }

    @Override
    public Recipients loadInBackground() {
        return DatabaseFactory.getGroupDatabase(getContext()).getGroupMembers(groupId, true);
    }
}
