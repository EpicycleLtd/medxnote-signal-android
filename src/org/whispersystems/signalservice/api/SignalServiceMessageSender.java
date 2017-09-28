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
package org.whispersystems.signalservice.api;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.push.MismatchedDevices;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.push.PushAttachmentData;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.SendMessageResponse;
import org.whispersystems.signalservice.internal.push.StaleDevices;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * The main interface for sending Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceMessageSender {

  private static final String TAG = SignalServiceMessageSender.class.getSimpleName();

  private final PushServiceSocket       socket;
  private final SignalProtocolStore     store;
  private final SignalServiceAddress localAddress;
  private final Optional<EventListener> eventListener;

  /**
   * Construct a SignalServiceMessageSender.
   *
   * @param url The URL of the Signal Service.
   * @param trustStore The trust store containing the Signal Service's signing TLS certificate.
   * @param user The Signal Service username (eg phone number).
   * @param password The Signal Service user password.
   * @param store The SignalProtocolStore.
   * @param eventListener An optional event listener, which fires whenever sessions are
   *                      setup or torn down for a recipient.
   */
  public SignalServiceMessageSender(String url, TrustStore trustStore,
                                    String user, String password,
                                    SignalProtocolStore store,
                                    String userAgent,
                                    Optional<EventListener> eventListener)
  {
    this.socket        = new PushServiceSocket(url, trustStore, new StaticCredentialsProvider(user, password, null), userAgent);
    this.store         = store;
    this.localAddress  = new SignalServiceAddress(user);
    this.eventListener = eventListener;
  }

  /**
   * Send a read receipt for a read message.  It is not necessary to call this
   * when receiving messages through {@link SignalServiceMessagePipe}.
   *
   * @param recipient The sender of the received message you're acknowledging.
   * @param messageId The message id of the received message you're acknowledging.
   * @throws IOException
   */
  public void sendDeliveryRead(SignalServiceAddress recipient, long messageId) throws IOException {
    this.socket.sendRead(recipient.getNumber(), messageId, recipient.getRelay());
  }

  /**
   * Send a delivery receipt for a received message.  It is not necessary to call this
   * when receiving messages through {@link SignalServiceMessagePipe}.
   *
   * @param recipient The sender of the received message you're acknowledging.
   * @param messageId The message id of the received message you're acknowledging.
   * @throws IOException
   */
  public void sendDeliveryReceipt(SignalServiceAddress recipient, long messageId) throws IOException {
    this.socket.sendReceipt(recipient.getNumber(), messageId, recipient.getRelay());
  }

  /**
   * Send a message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The message.
   * @throws UntrustedIdentityException
   * @throws IOException
   */
  public void sendMessage(SignalServiceAddress recipient, SignalServiceDataMessage message)
          throws UntrustedIdentityException, IOException
  {
    byte[]              content   = createMessageContent(message);
    long                timestamp = message.getTimestamp();
    SendMessageResponse response  = sendMessage(recipient, timestamp, content, true);

    if (response != null && response.getNeedsSync()) {
      byte[] syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.of(recipient), timestamp);
      sendMessage(localAddress, timestamp, syncMessage, false);
    }

    if (message.isEndSession()) {
      store.deleteAllSessions(recipient.getNumber());

      if (eventListener.isPresent()) {
        eventListener.get().onSecurityEvent(recipient);
      }
    }
  }

  /**
   * Send a message to a group.
   *
   * @param recipients The group members.
   * @param message The group message.
   * @throws IOException
   * @throws EncapsulatedExceptions
   */
  public void sendMessage(List<SignalServiceAddress> recipients, SignalServiceDataMessage message)
          throws IOException, EncapsulatedExceptions
  {
    byte[]              content   = createMessageContent(message);
    long                timestamp = message.getTimestamp();
    SendMessageResponse response  = sendMessage(recipients, timestamp, content, true);

    try {
      if (response != null && response.getNeedsSync()) {
        byte[] syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.<SignalServiceAddress>absent(), timestamp);
        sendMessage(localAddress, timestamp, syncMessage, false);
      }
    } catch (UntrustedIdentityException e) {
      throw new EncapsulatedExceptions(e);
    }
  }

  public void sendMessage(SignalServiceSyncMessage message)
          throws IOException, UntrustedIdentityException
  {
    byte[] content;

    if (message.getContacts().isPresent()) {
      content = createMultiDeviceContactsContent(message.getContacts().get().asStream());
    } else if (message.getGroups().isPresent()) {
      content = createMultiDeviceGroupsContent(message.getGroups().get().asStream());
    } else if (message.getRead().isPresent()) {
      content = createMultiDeviceReadContent(message.getRead().get());
    } else {
      throw new IOException("Unsupported sync message!");
    }

    sendMessage(localAddress, System.currentTimeMillis(), content, false);
  }

  private byte[] createMessageContent(SignalServiceDataMessage message) throws IOException {
    DataMessage.Builder     builder  = DataMessage.newBuilder();
    List<AttachmentPointer> pointers = createAttachmentPointers(message.getAttachments());

    if (!pointers.isEmpty()) {
      builder.addAllAttachments(pointers);
    }

    if (message.getBody().isPresent()) {
      builder.setBody(message.getBody().get());
    }

    if (message.getGroupInfo().isPresent()) {
      builder.setGroup(createGroupContent(message.getGroupInfo().get()));
    }

    if (message.isEndSession()) {
      builder.setFlags(DataMessage.Flags.END_SESSION_VALUE);
    }

    return builder.build().toByteArray();
  }

  private byte[] createMultiDeviceContactsContent(SignalServiceAttachmentStream contacts) throws IOException {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = SyncMessage.newBuilder();
    builder.setContacts(SyncMessage.Contacts.newBuilder()
            .setBlob(createAttachmentPointer(contacts)));

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceGroupsContent(SignalServiceAttachmentStream groups) throws IOException {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = SyncMessage.newBuilder();
    builder.setGroups(SyncMessage.Groups.newBuilder()
            .setBlob(createAttachmentPointer(groups)));

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceSentTranscriptContent(byte[] content, Optional<SignalServiceAddress> recipient, long timestamp) {
    try {
      Content.Builder          container   = Content.newBuilder();
      SyncMessage.Builder      syncMessage = SyncMessage.newBuilder();
      SyncMessage.Sent.Builder sentMessage = SyncMessage.Sent.newBuilder();

      sentMessage.setTimestamp(timestamp);
      sentMessage.setMessage(DataMessage.parseFrom(content));

      if (recipient.isPresent()) {
        sentMessage.setDestination(recipient.get().getNumber());
      }

      return container.setSyncMessage(syncMessage.setSent(sentMessage)).build().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] createMultiDeviceReadContent(List<ReadMessage> readMessages) {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = SyncMessage.newBuilder();

    for (ReadMessage readMessage : readMessages) {
      builder.addRead(SyncMessage.Read.newBuilder()
              .setTimestamp(readMessage.getTimestamp())
              .setSender(readMessage.getSender()));
    }

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private GroupContext createGroupContent(SignalServiceGroup group) throws IOException {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    SignalServiceGroup.Type type = group.getType();
    if (type != SignalServiceGroup.Type.DELIVER) {
      if (type == SignalServiceGroup.Type.UPDATE) {
        builder.setType(GroupContext.Type.UPDATE);
      } else if (type == SignalServiceGroup.Type.QUIT) {
        builder.setType(GroupContext.Type.QUIT);
      } else if (type == SignalServiceGroup.Type.KICK) {
        builder.setType(GroupContext.Type.KICK);
      } else {
        throw new AssertionError("Unknown type: " + group.getType());
      }

      if (group.getName().isPresent()) builder.setName(group.getName().get());
      if (group.getMembers().isPresent()) builder.addAllMembers(group.getMembers().get());
      if (group.getKick().isPresent()) builder.addAllKick(group.getKick().get());

      if (group.getAvatar().isPresent() && group.getAvatar().get().isStream()) {
        AttachmentPointer pointer = createAttachmentPointer(group.getAvatar().get().asStream());
        builder.setAvatar(pointer);
      }
    } else {
      builder.setType(GroupContext.Type.DELIVER);
    }

    if (group.getAdmin() != null && !group.getAdmin().isEmpty()) {
      builder.setAdmin(group.getAdmin());
    }

    return builder.build();
  }

  private SendMessageResponse sendMessage(List<SignalServiceAddress> recipients, long timestamp, byte[] content, boolean legacy)
          throws IOException, EncapsulatedExceptions
  {
    List<UntrustedIdentityException> untrustedIdentities = new LinkedList<>();
    List<UnregisteredUserException>  unregisteredUsers   = new LinkedList<>();
    List<NetworkFailureException>    networkExceptions   = new LinkedList<>();

    SendMessageResponse response = null;

    for (SignalServiceAddress recipient : recipients) {
      try {
        response = sendMessage(recipient, timestamp, content, legacy);
      } catch (UntrustedIdentityException e) {
        Log.w(TAG, e);
        untrustedIdentities.add(e);
      } catch (UnregisteredUserException e) {
        Log.w(TAG, e);
        unregisteredUsers.add(e);
      } catch (PushNetworkException e) {
        Log.w(TAG, e);
        networkExceptions.add(new NetworkFailureException(recipient.getNumber(), e));
      }
    }

    if (!untrustedIdentities.isEmpty() || !unregisteredUsers.isEmpty() || !networkExceptions.isEmpty()) {
      throw new EncapsulatedExceptions(untrustedIdentities, unregisteredUsers, networkExceptions);
    }

    return response;
  }

  private SendMessageResponse sendMessage(SignalServiceAddress recipient, long timestamp, byte[] content, boolean legacy)
          throws UntrustedIdentityException, IOException
  {
    for (int i=0;i<3;i++) {
      try {
        OutgoingPushMessageList messages = getEncryptedMessages(socket, recipient, timestamp, content, legacy);
        return socket.sendMessage(messages);
      } catch (MismatchedDevicesException mde) {
        Log.w(TAG, mde);
        handleMismatchedDevices(socket, recipient, mde.getMismatchedDevices());
      } catch (StaleDevicesException ste) {
        Log.w(TAG, ste);
        handleStaleDevices(recipient, ste.getStaleDevices());
      }
    }

    throw new IOException("Failed to resolve conflicts after 3 attempts!");
  }

  private List<AttachmentPointer> createAttachmentPointers(Optional<List<SignalServiceAttachment>> attachments) throws IOException {
    List<AttachmentPointer> pointers = new LinkedList<>();

    if (!attachments.isPresent() || attachments.get().isEmpty()) {
      Log.w(TAG, "No attachments present...");
      return pointers;
    }

    for (SignalServiceAttachment attachment : attachments.get()) {
      if (attachment.isStream()) {
        Log.w(TAG, "Found attachment, creating pointer...");
        pointers.add(createAttachmentPointer(attachment.asStream()));
      }
    }

    return pointers;
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentStream attachment)
          throws IOException
  {
    byte[]             attachmentKey  = Util.getSecretBytes(64);
    PushAttachmentData attachmentData = new PushAttachmentData(attachment.getContentType(),
            attachment.getInputStream(),
            attachment.getLength(),
            attachment.getListener(),
            attachmentKey);

    long attachmentId = socket.sendAttachment(attachmentData);

    AttachmentPointer.Builder builder = AttachmentPointer.newBuilder()
            .setContentType(attachment.getContentType())
            .setId(attachmentId)
            .setKey(ByteString.copyFrom(attachmentKey))
            .setSize((int)attachment.getLength());

    if (attachment.getPreview().isPresent()) {
      builder.setThumbnail(ByteString.copyFrom(attachment.getPreview().get()));
    }

    return builder.build();
  }


  private OutgoingPushMessageList getEncryptedMessages(PushServiceSocket socket,
                                                       SignalServiceAddress recipient,
                                                       long timestamp,
                                                       byte[] plaintext,
                                                       boolean legacy)
          throws IOException, UntrustedIdentityException
  {
    List<OutgoingPushMessage> messages = new LinkedList<>();

    if (!recipient.equals(localAddress)) {
      messages.add(getEncryptedMessage(socket, recipient, SignalServiceAddress.DEFAULT_DEVICE_ID, plaintext, legacy));
    }

    for (int deviceId : store.getSubDeviceSessions(recipient.getNumber())) {
      messages.add(getEncryptedMessage(socket, recipient, deviceId, plaintext, legacy));
    }

    return new OutgoingPushMessageList(recipient.getNumber(), timestamp, recipient.getRelay().orNull(), messages);
  }

  private OutgoingPushMessage getEncryptedMessage(PushServiceSocket socket, SignalServiceAddress recipient, int deviceId, byte[] plaintext, boolean legacy)
          throws IOException, UntrustedIdentityException
  {
    SignalProtocolAddress signalProtocolAddress = new SignalProtocolAddress(recipient.getNumber(), deviceId);
    SignalServiceCipher cipher                = new SignalServiceCipher(localAddress, store);

    if (!store.containsSession(signalProtocolAddress)) {
      try {
        List<PreKeyBundle> preKeys = socket.getPreKeys(recipient, deviceId);

        for (PreKeyBundle preKey : preKeys) {
          try {
            SignalProtocolAddress preKeyAddress  = new SignalProtocolAddress(recipient.getNumber(), preKey.getDeviceId());
            SessionBuilder        sessionBuilder = new SessionBuilder(store, preKeyAddress);
            sessionBuilder.process(preKey);
          } catch (org.whispersystems.libsignal.UntrustedIdentityException e) {
            throw new UntrustedIdentityException("Untrusted identity key!", recipient.getNumber(), preKey.getIdentityKey());
          }
        }

        if (eventListener.isPresent()) {
          eventListener.get().onSecurityEvent(recipient);
        }
      } catch (InvalidKeyException e) {
        throw new IOException(e);
      }
    }

    return cipher.encrypt(signalProtocolAddress, plaintext, legacy);
  }

  private void handleMismatchedDevices(PushServiceSocket socket, SignalServiceAddress recipient,
                                       MismatchedDevices mismatchedDevices)
          throws IOException, UntrustedIdentityException
  {
    try {
      for (int extraDeviceId : mismatchedDevices.getExtraDevices()) {
        store.deleteSession(new SignalProtocolAddress(recipient.getNumber(), extraDeviceId));
      }

      for (int missingDeviceId : mismatchedDevices.getMissingDevices()) {
        PreKeyBundle preKey = socket.getPreKey(recipient, missingDeviceId);

        try {
          SessionBuilder sessionBuilder = new SessionBuilder(store, new SignalProtocolAddress(recipient.getNumber(), missingDeviceId));
          sessionBuilder.process(preKey);
        } catch (org.whispersystems.libsignal.UntrustedIdentityException e) {
          throw new UntrustedIdentityException("Untrusted identity key!", recipient.getNumber(), preKey.getIdentityKey());
        }
      }
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private void handleStaleDevices(SignalServiceAddress recipient, StaleDevices staleDevices) {
    for (int staleDeviceId : staleDevices.getStaleDevices()) {
      store.deleteSession(new SignalProtocolAddress(recipient.getNumber(), staleDeviceId));
    }
  }

  public interface EventListener {
    void onSecurityEvent(SignalServiceAddress address);
  }

}
