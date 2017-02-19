//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.whispersystems.libsignal.util;

import android.util.Log;
import android.util.SparseIntArray;
import org.whispersystems.libsignal.logging.SignalProtocolLogger;

public class AndroidSignalProtocolLogger implements SignalProtocolLogger {
  private static final SparseIntArray PRIORITY_MAP = new SparseIntArray(5) {
    {
      this.put(4, 4);
      this.put(7, 7);
      this.put(3, 3);
      this.put(2, 2);
      this.put(5, 5);
    }
  };

  public AndroidSignalProtocolLogger() {
  }

  public void log(int priority, String tag, String message) {
    int androidPriority = PRIORITY_MAP.get(priority, 5);
    Log.println(androidPriority, tag, message);
  }
}
