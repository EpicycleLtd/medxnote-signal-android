package com.medxnote.securesms;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medxnote.securesms.components.AvatarImageView;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.util.Util;


public class GroupMembersListItem extends LinearLayout {

    private AvatarImageView contactPhotoImage;
    private TextView        nameView;
    private TextView        numberView;
    private TextView        statusView;

    private Recipient       recipient;

    public GroupMembersListItem(Context context) {
        super(context);
    }

    public GroupMembersListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        this.contactPhotoImage = (AvatarImageView) findViewById(R.id.contact_photo_image);
        this.nameView = (TextView) findViewById(R.id.name);
        this.numberView = (TextView) findViewById(R.id.number);
        this.statusView = (TextView) findViewById(R.id.status_label);
    }

    public void set(String number, String name, boolean isAdmin) {

        recipient = RecipientFactory.getRecipientsFromString(getContext(),
                number, false).getPrimaryRecipient();

        contactPhotoImage.setAvatar(recipient, true);

        if (isAdmin) {
            statusView.setVisibility(VISIBLE);
        } else {
            statusView.setVisibility(GONE);
        }

        setText(name, number);
    }

    private void setText(String name, String number) {
        if (TextUtils.isEmpty(name)) {
            nameView.setVisibility(GONE);
        } else {
            nameView.setText(name);
        }

        if (Util.isOwnNumber(getContext(), number)) {
            nameView.setText(getContext().getString(R.string.GroupMembersListItem_me));
            nameView.setVisibility(VISIBLE);
        }

        numberView.setText(number);
    }

    public Recipient getRecipient() {
        return recipient;
    }

}
