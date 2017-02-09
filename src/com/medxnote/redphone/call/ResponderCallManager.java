/*
 * Copyright (C) 2011 Whisper Systems
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

package com.medxnote.redphone.call;

import android.content.Context;
import android.util.Log;

import com.medxnote.redphone.crypto.zrtp.MasterSecret;
import com.medxnote.redphone.network.RtpSocket;
import com.medxnote.redphone.signaling.OtpCounterProvider;
import com.medxnote.redphone.signaling.SessionDescriptor;
import com.medxnote.redphone.signaling.SessionInitiationFailureException;
import com.medxnote.redphone.signaling.SessionStaleException;
import com.medxnote.redphone.audio.AudioException;
import com.medxnote.redphone.audio.CallAudioManager;
import com.medxnote.redphone.crypto.SecureRtpSocket;
import com.medxnote.redphone.crypto.zrtp.ZRTPResponderSocket;
import com.medxnote.redphone.signaling.LoginFailedException;
import com.medxnote.redphone.signaling.NetworkConnector;
import com.medxnote.redphone.signaling.SignalingException;
import com.medxnote.redphone.signaling.SignalingSocket;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * CallManager responsible for coordinating incoming calls.
 *
 * @author Moxie Marlinspike
 *
 */
public class ResponderCallManager extends CallManager {

  private static final String TAG = ResponderCallManager.class.getSimpleName();

  private final String localNumber;
  private final String password;
  private final byte[] zid;

  private int answer = 0;

  public ResponderCallManager(Context context, CallStateListener callStateListener,
                              String remoteNumber, String localNumber,
                              String password, SessionDescriptor sessionDescriptor,
                              byte[] zid)
  {
    super(context, callStateListener, remoteNumber, "ResponderCallManager Thread");
    this.localNumber       = localNumber;
    this.password          = password;
    this.sessionDescriptor = sessionDescriptor;
    this.zid               = zid;
  }

  @Override
  public void run() {
    try {
      signalingSocket = new SignalingSocket(context,
                                            sessionDescriptor.getFullServerName(),
                                            31337,
                                            localNumber, password,
                                            OtpCounterProvider.getInstance());

      signalingSocket.setRinging(sessionDescriptor.sessionId);
      callStateListener.notifyCallFresh();

      processSignals();

      if (!waitForAnswer()) {
        return;
      }

      int localPort = new NetworkConnector(sessionDescriptor.sessionId,
                                           sessionDescriptor.getFullServerName(),
                                           sessionDescriptor.relayPort).makeConnection();

      InetSocketAddress remoteAddress = new InetSocketAddress(sessionDescriptor.getFullServerName(),
                                                              sessionDescriptor.relayPort);

      secureSocket  = new SecureRtpSocket(new RtpSocket(localPort, remoteAddress));
      zrtpSocket    = new ZRTPResponderSocket(context, secureSocket, zid, remoteNumber, sessionDescriptor.version <= 0);

      callStateListener.notifyConnectingtoInitiator();

      super.run();
    } catch (SignalingException | SessionInitiationFailureException se) {
      Log.w(TAG, se);
      callStateListener.notifyServerFailure();
    } catch (SessionStaleException e) {
      Log.w(TAG, e);
      callStateListener.notifyCallStale();
    } catch (LoginFailedException lfe) {
      Log.w(TAG, lfe);
      callStateListener.notifyLoginFailed();
    } catch (SocketException e) {
      Log.w(TAG, e);
      callStateListener.notifyCallDisconnected();
    } catch( RuntimeException e ) {
      Log.e(TAG, "Died unhandled with exception!");
      Log.w(TAG, e);
      callStateListener.notifyClientFailure();
    }
  }

  public synchronized void answer(boolean answer) {
    this.answer = (answer ? 1 : 2);
    notifyAll();
  }

  private synchronized boolean waitForAnswer() {
    try {
      while (answer == 0)
        wait();
    } catch (InterruptedException ie) {
      throw new IllegalArgumentException(ie);
    }

    return this.answer == 1;
  }

  @Override
  public void terminate() {
    synchronized (this) {
      if (answer == 0) {
        answer(false);
      }
    }

    super.terminate();
  }

  @Override
  protected void runAudio(DatagramSocket socket, String remoteIp, int remotePort,
                          MasterSecret masterSecret, boolean muteEnabled)
      throws SocketException, AudioException
  {
    this.callAudioManager = new CallAudioManager(socket, remoteIp, remotePort,
                                                 masterSecret.getResponderSrtpKey(),
                                                 masterSecret.getResponderMacKey(),
                                                 masterSecret.getResponderSrtpSailt(),
                                                 masterSecret.getInitiatorSrtpKey(),
                                                 masterSecret.getInitiatorMacKey(),
                                                 masterSecret.getInitiatorSrtpSalt());
    this.callAudioManager.setMute(muteEnabled);
    this.callAudioManager.start(context);
  }

}
