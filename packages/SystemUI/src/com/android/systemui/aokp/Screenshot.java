/*
 * Copyright 2011 Colin McDonough
 *
 * Modified for AOKP by Mike Wilson - Zaphod-Beeblebrox
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.aokp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;


public class Screenshot extends Activity {
  public Screenshot() {
    super();
    }

    final Object mScreenshotLock = new Object();
    ServiceConnection mScreenshotConnection = null;

    private Handler H = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {

            }
        }
    };

    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(H.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }

                        /* take the screenshot */
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                      Screenshot.this.finish();
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
            if (bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
            }
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
             takeScreenshot();
    }

    public void onDestroy() {
        super.onDestroy();
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
    }
}