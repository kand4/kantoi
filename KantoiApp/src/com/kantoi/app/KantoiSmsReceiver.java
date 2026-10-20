package com.kantoi.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class KantoiSmsReceiver extends BroadcastReceiver {
    private static final String TAG = "KantoiSmsReceiver";
    public static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;
        try {
            String action = intent.getAction();
            if (SMS_RECEIVED_ACTION.equals(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle == null) return;

                Object[] pdus = (Object[]) bundle.get("pdus");
                String format = bundle.getString("format");
                if (pdus == null || pdus.length == 0) return;

                StringBuilder fullBody = new StringBuilder();
                String sender = null;

                for (Object pdu : pdus) {
                    SmsMessage message;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        message = SmsMessage.createFromPdu((byte[]) pdu, format);
                    } else {
                        message = SmsMessage.createFromPdu((byte[]) pdu);
                    }

                    if (message != null) {
                        if (sender == null) {
                            sender = message.getDisplayOriginatingAddress();
                        }
                        String bodyPart = message.getMessageBody();
                        if (bodyPart != null) {
                            fullBody.append(bodyPart);
                        }
                    }
                }

                if (sender != null && fullBody.length() > 0) {
                    String body = fullBody.toString();
                    Log.i(TAG, "Incoming SMS from [" + sender + "]: " + ((body.length() > 60) ? body.substring(0, 60) + "..." : body));

                    KantoiThreatEngine engine = KantoiThreatEngine.getInstance(context);
                    engine.analyzeAndHandleSms(context, sender, body);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onReceive: " + t.getMessage(), t);
        }
    }
}
