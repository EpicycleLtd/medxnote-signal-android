package com.medxnote.securesms.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.medxnote.securesms.attachments.AttachmentId;
import com.medxnote.securesms.crypto.MasterSecret;
import com.medxnote.securesms.database.DatabaseFactory;
import com.medxnote.securesms.providers.PartProvider;
import com.medxnote.securesms.providers.PersistentBlobProvider;
import com.medxnote.securesms.providers.SingleUseBlobProvider;

import java.io.IOException;
import java.io.InputStream;

public class PartAuthority {

  private static final String PART_URI_STRING   = "content://com.medxnote.securesms/part";
  private static final String THUMB_URI_STRING  = "content://com.medxnote.securesms/thumb";
  private static final Uri    PART_CONTENT_URI  = Uri.parse(PART_URI_STRING);
  private static final Uri    THUMB_CONTENT_URI = Uri.parse(THUMB_URI_STRING);

  private static final int PART_ROW       = 1;
  private static final int THUMB_ROW      = 2;
  private static final int PERSISTENT_ROW = 3;
  private static final int SINGLE_USE_ROW = 4;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("com.medxnote.securesms", "part/*/#", PART_ROW);
    uriMatcher.addURI("com.medxnote.securesms", "thumb/*/#", THUMB_ROW);
    uriMatcher.addURI(PersistentBlobProvider.AUTHORITY, PersistentBlobProvider.EXPECTED_PATH, PERSISTENT_ROW);
    uriMatcher.addURI(SingleUseBlobProvider.AUTHORITY, SingleUseBlobProvider.PATH, SINGLE_USE_ROW);
  }

  public static InputStream getAttachmentStream(@NonNull Context context, @NonNull MasterSecret masterSecret, @NonNull Uri uri)
      throws IOException
  {
    int match = uriMatcher.match(uri);
    try {
      switch (match) {
      case PART_ROW:
        PartUriParser partUri = new PartUriParser(uri);
        return DatabaseFactory.getAttachmentDatabase(context).getAttachmentStream(masterSecret, partUri.getPartId());
      case THUMB_ROW:
        partUri = new PartUriParser(uri);
        return DatabaseFactory.getAttachmentDatabase(context).getThumbnailStream(masterSecret, partUri.getPartId());
      case PERSISTENT_ROW:
        return PersistentBlobProvider.getInstance(context).getStream(masterSecret, ContentUris.parseId(uri));
      case SINGLE_USE_ROW:
        return SingleUseBlobProvider.getInstance().getStream(ContentUris.parseId(uri));
      default:
        return context.getContentResolver().openInputStream(uri);
      }
    } catch (SecurityException se) {
      throw new IOException(se);
    }
  }

  public static Uri getAttachmentPublicUri(Uri uri) {
    PartUriParser partUri = new PartUriParser(uri);
    return PartProvider.getContentUri(partUri.getPartId());
  }

  public static Uri getAttachmentDataUri(AttachmentId attachmentId) {
    Uri uri = Uri.withAppendedPath(PART_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
    return ContentUris.withAppendedId(uri, attachmentId.getRowId());
  }

  public static Uri getAttachmentThumbnailUri(AttachmentId attachmentId) {
    Uri uri = Uri.withAppendedPath(THUMB_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
    return ContentUris.withAppendedId(uri, attachmentId.getRowId());
  }

  public static boolean isLocalUri(final @NonNull Uri uri) {
    int match = uriMatcher.match(uri);
    switch (match) {
    case PART_ROW:
    case THUMB_ROW:
    case PERSISTENT_ROW:
    case SINGLE_USE_ROW:
      return true;
    }
    return false;
  }
}
