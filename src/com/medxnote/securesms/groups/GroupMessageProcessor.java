package com.medxnote.securesms.groups;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.ByteString;
import com.medxnote.securesms.crypto.MasterSecretUnion;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.database.EncryptingSmsDatabase;
import com.medxnote.securesms.database.MmsDatabase;
import com.medxnote.securesms.mms.OutgoingGroupMediaMessage;
import com.medxnote.securesms.notifications.MessageNotifier;
import com.medxnote.securesms.sms.IncomingTextMessage;
import com.medxnote.securesms.util.Base64;
import com.medxnote.securesms.util.GroupUtil;

import com.medxnote.securesms.ApplicationContext;
import com.medxnote.securesms.database.GroupDatabase;
import com.medxnote.securesms.jobs.AvatarDownloadJob;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;
import com.medxnote.securesms.sms.IncomingGroupMessage;
import com.medxnote.securesms.util.TextSecurePreferences;
import com.medxnote.securesms.util.Util;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ws.com.google.android.mms.MmsException;

import static com.medxnote.securesms.database.GroupDatabase.GroupRecord;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

public class GroupMessageProcessor {

  private static final String TAG = GroupMessageProcessor.class.getSimpleName();

  public static @Nullable Long process(@NonNull Context context,
                                       @NonNull MasterSecretUnion masterSecret,
                                       @NonNull SignalServiceEnvelope envelope,
                                       @NonNull SignalServiceDataMessage message,
                                       boolean outgoing)
  {
    if (!message.getGroupInfo().isPresent() || message.getGroupInfo().get().getGroupId() == null) {
      Log.w(TAG, "Received group message with no id! Ignoring...");
      return null;
    }

    GroupDatabase      database  = DatabaseFactory.getGroupDatabase(context);
    SignalServiceGroup group     = message.getGroupInfo().get();
    byte[]             id        = group.getGroupId();
    GroupRecord        record    = database.getGroup(id);
    String             initiator = "";
    try {
      initiator = Util.canonicalizeNumber(context, envelope.getSource());
    } catch (InvalidNumberException e ){
      return null;
    }

    if (record != null) {
      if (group.getType() == SignalServiceGroup.Type.UPDATE) {
        if (record.getVersion() == 0 || record.isAdminUpdate(initiator)) {
          return handleGroupUpdate(context, masterSecret, envelope, group, record, outgoing);
        }
      } else if (group.getType() == SignalServiceGroup.Type.KICK) {
        if (record.getVersion() == 0 || record.isAdminUpdate(initiator)) {
          return handleGroupKick(context, masterSecret, envelope, group, record);
        }
      } else if (group.getType() == SignalServiceGroup.Type.QUIT) {
        return handleGroupLeave(context, masterSecret, envelope, group, record, outgoing);
      }
      return null;
    } else {
      if (group.getType() == SignalServiceGroup.Type.UPDATE) {
        return handleGroupCreate(context, masterSecret, envelope, group, outgoing);
      }
    }
    Log.w(TAG, "Received unknown type, ignoring...");
    return null;
  }

  private static @Nullable Long handleGroupCreate(@NonNull Context context,
                                                  @NonNull MasterSecretUnion masterSecret,
                                                  @NonNull SignalServiceEnvelope envelope,
                                                  @NonNull SignalServiceGroup group,
                                                  boolean outgoing)
  {
    GroupDatabase        database = DatabaseFactory.getGroupDatabase(context);
    byte[]               id       = group.getGroupId();
    GroupContext.Builder builder  = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    SignalServiceAttachment avatar = group.getAvatar().orNull();

    database.create(id, group.getName().orNull(), group.getMembers().orNull(),
            avatar != null && avatar.isPointer() ? avatar.asPointer() : null,
            envelope.getRelay(), group.getAdmin());

    RecipientFactory.clearCache();

    return storeMessage(context, masterSecret, envelope, group, builder.build(), outgoing);
  }

  private static @Nullable Long handleGroupKick(@NonNull Context context,
                                                @NonNull MasterSecretUnion masterSecret,
                                                @NonNull SignalServiceEnvelope envelope,
                                                @NonNull SignalServiceGroup group,
                                                @NonNull GroupRecord groupRecord) {

    Set<String> recordMembers = new HashSet<>(groupRecord.getMembers());
    Set<String> messageMembers = new HashSet<>(group.getMembers().get());
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    Set<String> addedMembers = new HashSet<>(messageMembers);
    addedMembers.removeAll(recordMembers);

    byte[] id = group.getGroupId();

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.KICK);

    if (group.getName().isPresent() || group.getAvatar().isPresent()) {
      SignalServiceAttachment avatar = group.getAvatar().orNull();
      database.update(id, group.getName().orNull(), avatar != null ? avatar.asPointer() : null);
    }

    GroupContext groupContext = builder.build();

    List<String> kick = group.getKick().get();

    Set<String> unionMembers = new HashSet<>(recordMembers);
    unionMembers.addAll(messageMembers);
    unionMembers.removeAll(kick);

    database.updateMembers(id, new LinkedList<>(unionMembers));

    String body = Base64.encodeBytes(groupContext.toByteArray());
    IncomingTextMessage incoming = new IncomingTextMessage(envelope.getSource(), envelope.getSourceDevice(),
            envelope.getTimestamp(), body, Optional.of(group));
    IncomingGroupMessage  groupMessage = new IncomingGroupMessage(incoming, groupContext, body);

    Pair<Long, Long> messageAndThreadId = smsDatabase.insertMessageInbox(masterSecret, groupMessage);

    if (kick.contains(TextSecurePreferences.getLocalNumber(context))) {
      database.setActive(id, false);
    }

    MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);
    return messageAndThreadId.second;
  }

  private static @Nullable Long handleGroupUpdate(@NonNull Context context,
                                                  @NonNull MasterSecretUnion masterSecret,
                                                  @NonNull SignalServiceEnvelope envelope,
                                                  @NonNull SignalServiceGroup group,
                                                  @NonNull GroupRecord groupRecord,
                                                  boolean outgoing)
  {

    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    byte[]        id       = group.getGroupId();

    Set<String> recordMembers = new HashSet<>(groupRecord.getMembers());
    Set<String> messageMembers = new HashSet<>(group.getMembers().get());

    Set<String> addedMembers = new HashSet<>(messageMembers);
    addedMembers.removeAll(recordMembers);

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    if (addedMembers.size() > 0) {
      Set<String> unionMembers = new HashSet<>(recordMembers);
      unionMembers.addAll(messageMembers);
      database.updateMembers(id, new LinkedList<>(unionMembers));

      builder.clearMembers().addAllMembers(addedMembers);
    } else {
      builder.clearMembers();
    }

    if (group.getName().isPresent() || group.getAvatar().isPresent()) {
      SignalServiceAttachment avatar = group.getAvatar().orNull();
      database.update(id, group.getName().orNull(), avatar != null ? avatar.asPointer() : null);
    }

    if (group.getName().isPresent() && group.getName().get().equals(groupRecord.getTitle())) {
      builder.clearName();
    }

    if (!groupRecord.getAdmin().equals(group.getAdmin())) {
      database.updateAdmin(group.getGroupId(), group.getAdmin());
    }

    if (!groupRecord.isActive()) {
      database.setActive(id, true);
    }

    return storeMessage(context, masterSecret, envelope, group, builder.build(), outgoing);
  }

  private static Long handleGroupLeave(@NonNull Context               context,
                                       @NonNull MasterSecretUnion     masterSecret,
                                       @NonNull SignalServiceEnvelope envelope,
                                       @NonNull SignalServiceGroup    group,
                                       @NonNull GroupRecord           record,
                                       boolean  outgoing)
  {
    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    byte[]        id       = group.getGroupId();
    List<String>  members  = record.getMembers();

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.QUIT);

    if (members.contains(envelope.getSource())) {
      database.remove(id, envelope.getSource());
      if (outgoing) database.setActive(id, false);

      return storeMessage(context, masterSecret, envelope, group, builder.build(), outgoing);
    }

    return null;
  }


  private static @Nullable Long storeMessage(@NonNull Context context,
                                             @NonNull MasterSecretUnion masterSecret,
                                             @NonNull SignalServiceEnvelope envelope,
                                             @NonNull SignalServiceGroup group,
                                             @NonNull GroupContext storage,
                                             boolean  outgoing)
  {
    if (group.getAvatar().isPresent()) {
      ApplicationContext.getInstance(context).getJobManager()
              .add(new AvatarDownloadJob(context, group.getGroupId()));
    }

    try {
      if (outgoing) {
        MmsDatabase mmsDatabase     = DatabaseFactory.getMmsDatabase(context);
        Recipients                recipients      = RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(group.getGroupId()), false);
        OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(recipients, storage, null, envelope.getTimestamp());
        long                      threadId        = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
        long                      messageId       = mmsDatabase.insertMessageOutbox(masterSecret, outgoingMessage, threadId, false, false/* not hidden */);

        mmsDatabase.markAsSent(messageId);

        return threadId;
      } else {
        EncryptingSmsDatabase smsDatabase  = DatabaseFactory.getEncryptingSmsDatabase(context);
        String                body         = Base64.encodeBytes(storage.toByteArray());
        IncomingTextMessage incoming     = new IncomingTextMessage(envelope.getSource(), envelope.getSourceDevice(), envelope.getTimestamp(), body, Optional.of(group));
        IncomingGroupMessage  groupMessage = new IncomingGroupMessage(incoming, storage, body);

        Pair<Long, Long> messageAndThreadId = smsDatabase.insertMessageInbox(masterSecret, groupMessage);
        MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), messageAndThreadId.second);

        return messageAndThreadId.second;
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }

    return null;
  }

  private static GroupContext.Builder createGroupContext(SignalServiceGroup group) {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getAvatar().isPresent() && group.getAvatar().get().isPointer()) {
      builder.setAvatar(AttachmentPointer.newBuilder()
              .setId(group.getAvatar().get().asPointer().getId())
              .setKey(ByteString.copyFrom(group.getAvatar().get().asPointer().getKey()))
              .setContentType(group.getAvatar().get().getContentType()));
    }

    if (group.getName().isPresent()) {
      builder.setName(group.getName().get());
    }

    if (group.getMembers().isPresent()) {
      builder.addAllMembers(group.getMembers().get());
    }

    if (group.getKick().isPresent()) {
      builder.addAllKick(group.getKick().get());
    }

    builder.setAdmin(group.getAdmin());

    return builder;
  }

}
