package com.medxnote.securesms.groups;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.protobuf.ByteString;
import com.medxnote.securesms.attachments.Attachment;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.AttachmentDatabase;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.mms.OutgoingGroupMediaMessage;
import com.medxnote.securesms.util.GroupUtil;
import com.medxnote.securesms.util.TextSecurePreferences;

import com.medxnote.securesms.attachments.UriAttachment;
import com.medxnote.securesms.database.GroupDatabase;
import com.medxnote.securesms.providers.SingleUseBlobProvider;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.sms.MessageSender;
import com.medxnote.securesms.util.BitmapUtil;
import com.medxnote.securesms.util.Util;

import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import ws.com.google.android.mms.ContentType;

public class GroupManager {
  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull  MasterSecret   masterSecret,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable Bitmap         avatar,
                                                       @Nullable String         name)
          throws InvalidNumberException
  {
    final byte[]        avatarBytes       = BitmapUtil.toByteArray(avatar);
    final GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    final byte[]        groupId           = groupDatabase.allocateGroupId();
    final Set<String>   memberE164Numbers = getE164Numbers(context, members);
          String        localNumber       = TextSecurePreferences.getLocalNumber(context);

    memberE164Numbers.add(localNumber);
    groupDatabase.create(groupId, name, new LinkedList<>(memberE164Numbers), null, null, localNumber);
    groupDatabase.updateAvatar(groupId, avatarBytes);
    return sendGroupUpdate(context, masterSecret, groupId, memberE164Numbers, new HashSet<String>(), name, avatarBytes, localNumber);
  }

  private static Set<String> getE164Numbers(Context context, Collection<Recipient> recipients)
          throws InvalidNumberException
  {
    final Set<String> results = new HashSet<>();
    if (recipients != null) {
      for (Recipient recipient : recipients) {
        results.add(Util.canonicalizeNumber(context, recipient.getNumber()));
      }
    }
    return results;
  }

  public static GroupActionResult updateGroup(@NonNull  Context        context,
                                              @NonNull  MasterSecret   masterSecret,
                                              @NonNull  byte[]         groupId,
                                              @NonNull  Set<Recipient> members,
                                              @Nullable Set<Recipient> kickMembers,
                                              @Nullable Bitmap         avatar,
                                              @Nullable String         name,
                                              @Nullable Recipient      newAdmin)
          throws InvalidNumberException
  {
    final GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    final Set<String>   memberE164Numbers = getE164Numbers(context, members);
    final Set<String>   kickE164Numbers   = getE164Numbers(context, kickMembers);
    final byte[]        avatarBytes       = BitmapUtil.toByteArray(avatar);

    String admin;
    if (newAdmin == null) {
      admin = groupDatabase.getAdmin(groupId);
    } else {
      admin = newAdmin.getNumber();
    }

    memberE164Numbers.add(TextSecurePreferences.getLocalNumber(context));
    groupDatabase.updateMembers(groupId, new LinkedList<>(memberE164Numbers));
    groupDatabase.updateTitle(groupId, name);
    groupDatabase.updateAvatar(groupId, avatarBytes);
    groupDatabase.updateAdmin(groupId, admin);

    return sendGroupUpdate(context, masterSecret, groupId, memberE164Numbers,
            kickE164Numbers, name, avatarBytes, admin);
  }

  private static GroupActionResult sendGroupUpdate(@NonNull  Context      context,
                                                   @NonNull  MasterSecret masterSecret,
                                                   @NonNull  byte[]       groupId,
                                                   @NonNull  Set<String>  e164numbers,
                                                   @NonNull  Set<String>  kickE164numbers,
                                                   @Nullable String       groupName,
                                                   @Nullable byte[]       avatar,
                                                   @Nullable String       admin)
  {
    Attachment avatarAttachment = null;
    String     groupRecipientId = GroupUtil.getEncodedId(groupId);
    Recipients groupRecipient   = RecipientFactory.getRecipientsFromString(context, groupRecipientId, false);

    GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
            .setId(ByteString.copyFrom(groupId))
            .setAdmin(admin)
            .addAllMembers(e164numbers);
    if (groupName != null) {
      groupContextBuilder.setName(groupName);
    }

    if (avatar != null) {
      Uri avatarUri = SingleUseBlobProvider.getInstance().createUri(avatar);
      avatarAttachment = new UriAttachment(avatarUri, ContentType.IMAGE_PNG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length);
    }

    if (!kickE164numbers.isEmpty()) {
      groupContextBuilder.addAllKick(kickE164numbers);
      groupContextBuilder.setType(GroupContext.Type.KICK);
    } else {
      groupContextBuilder.setType(GroupContext.Type.UPDATE);
    }
    GroupContext groupContext = groupContextBuilder.build();

    OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis());
    long                      threadId        = MessageSender.send(context, masterSecret, outgoingMessage, -1, false);

    return new GroupActionResult(groupRecipient, threadId);
  }

  public static class GroupActionResult {
    private Recipients groupRecipient;
    private long       threadId;

    public GroupActionResult(Recipients groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }

    public Recipients getGroupRecipient() {
      return groupRecipient;
    }

    public long getThreadId() {
      return threadId;
    }
  }
}
