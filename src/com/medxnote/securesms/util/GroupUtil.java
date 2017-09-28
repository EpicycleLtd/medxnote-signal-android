package com.medxnote.securesms.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.medxnote.securesms.R;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.recipients.Recipient;
import com.medxnote.securesms.recipients.RecipientFactory;
import com.medxnote.securesms.recipients.Recipients;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext.Type;

public class GroupUtil {

  private static final String ENCODED_GROUP_PREFIX = "__textsecure_group__!";
  private static final String TAG                  = GroupUtil.class.getSimpleName();

  public static String getEncodedId(byte[] groupId) {
    return ENCODED_GROUP_PREFIX + Hex.toStringCondensed(groupId);
  }

  public static byte[] getDecodedId(String groupId) throws IOException {
    if (!isEncodedGroup(groupId)) {
      throw new IOException("Invalid encoding");
    }

    return Hex.fromStringCondensed(groupId.split("!", 2)[1]);
  }

  public static boolean isEncodedGroup(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_GROUP_PREFIX);
  }

  public static @NonNull GroupDescription getDescription(@NonNull Context context, @Nullable String encodedGroup) {
    if (encodedGroup == null) {
      return new GroupDescription(context, null);
    }

    try {
      GroupContext  groupContext = GroupContext.parseFrom(Base64.decode(encodedGroup));
      return new GroupDescription(context, groupContext);
    } catch (IOException e) {
      Log.w(TAG, e);
      return new GroupDescription(context, null);
    }
  }

  public static Set<Recipient> getMembers(@NonNull Context context, @NonNull List<String> numbers) {
    if (!numbers.isEmpty()) {
      Set<Recipient> recipientSet = new HashSet<>();

      for (String number : numbers) {
        Recipient recipient = RecipientFactory.getRecipientsFromString(context, number, false)
                .getPrimaryRecipient();

        recipientSet.add(recipient);
      }
      return recipientSet;
    }
    return null;
  }

  public static boolean isAdmin(@NonNull Context context,
                                @NonNull byte[] groupId) {
    String admin = DatabaseFactory.getGroupDatabase(context).getAdmin(groupId);
    String localNumber = TextSecurePreferences.getLocalNumber(context);
    return admin != null && admin.equals(localNumber);
  }

  public static class GroupDescription {

    @NonNull  private final Context         context;
    @Nullable private final GroupContext    groupContext;
    @Nullable private final Recipients      members;

    public GroupDescription(@NonNull Context context, @Nullable GroupContext groupContext) {
      this.context      = context.getApplicationContext();
      this.groupContext = groupContext;

      if (groupContext == null || groupContext.getMembersList().isEmpty()) {
        this.members = null;
      } else {
        this.members = RecipientFactory.getRecipientsFromString(context, Util.join(groupContext.getMembersList(), ", "), true);
      }
    }

    public String toString() {
      if (groupContext == null) {
        return context.getString(R.string.GroupUtil_group_updated);
      }

      StringBuilder description = new StringBuilder();
      String        title       = groupContext.getName();
      Type          type        = groupContext.getType();

      if (type.equals(Type.KICK)) {
        addKickDescription(description, groupContext.getKickList());
      }

      if (type.equals(Type.UPDATE)) {
        if (members != null) {
          description.append(context.getResources().getQuantityString(R.plurals.GroupUtil_joined_the_group,
                  members.getRecipientsList().size(), members.toShortString()));
        }
        if (title != null && !title.trim().isEmpty()) {
          if (description.length() > 0) description.append(" ");
          description.append(context.getString(R.string.GroupUtil_group_name_is_now, title));
        }

      }

      if (description.length() > 0) {
        return description.toString();
      } else {
        return context.getString(R.string.GroupUtil_group_updated);
      }
    }

    private void addKickDescription(StringBuilder description, List<String> kickMembers) {
      if (!kickMembers.isEmpty()) {
          int index = 0;
          int lastIndex = kickMembers.size() - 1;
          for (String number : kickMembers) {
            Recipient recipient = RecipientFactory.getRecipientsFromString(context, number, false)
                    .getPrimaryRecipient();
            if (recipient != null) {
              description.append(recipient.toShortString());
            } else {
              description.append(number);
            }
            if (index != lastIndex) {
              description.append(", ");
            } else {
              description.append(" ");
            }
          }
          description.append(context.getString(R.string.GroupUtil_kicked_out_of_the_group));
      }
    }

    public void addListener(Recipients.RecipientsModifiedListener listener) {
      if (this.members != null) {
        this.members.addListener(listener);
      }
    }
  }
}
