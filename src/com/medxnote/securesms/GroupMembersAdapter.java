package com.medxnote.securesms;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.medxnote.securesms.recipients.Recipient;

import java.util.List;


public class GroupMembersAdapter extends RecyclerView.Adapter<GroupMembersAdapter.ViewHolder> {

    private List<Recipient> recipients;
    private Recipient admin;
    private LayoutInflater inflater;

    static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View itemView) {
            super(itemView);
        }

        public GroupMembersListItem getItem() {
            return (GroupMembersListItem) itemView;
        }
    }

    public GroupMembersAdapter(Context context, List<Recipient> recipients, Recipient admin) {
        this.inflater = LayoutInflater.from(context);
        this.recipients = recipients;
        this.admin = admin;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        GroupMembersListItem item = (GroupMembersListItem)
                inflater.inflate(R.layout.group_members_list_item, parent, false);
        return new ViewHolder(item);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Recipient recipient = recipients.get(position);
        if (admin != null) {
            holder.getItem().set(recipient.getNumber(),
                    recipient.getName(), recipient.equals(admin));
        } else {
            holder.getItem().set(recipient.getNumber(),
                    recipient.getName(), true);
        }
    }

    @Override
    public int getItemCount() {
        return recipients.size();
    }

    public void swap(List<Recipient> recipients) {
        this.recipients = recipients;
    }
}
