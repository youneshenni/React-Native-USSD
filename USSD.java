package com.siraj.topup.USSD;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import com.klinker.android.send_message.Message;
import com.klinker.android.send_message.Settings;
import com.klinker.android.send_message.Transaction;
import com.romellfudi.ussdlibrary.USSDController;
import com.tuenti.smsradar.Sms;
import com.tuenti.smsradar.SmsListener;
import com.tuenti.smsradar.SmsRadar;
import android.annotation.SuppressLint;
import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

public class USSD extends ReactContextBaseJavaModule {

    private final TelephonyManager[] SIMManagers = new TelephonyManager[3];
    private final HashMap map = new HashMap<>();
    private int SIMCount = 0;
    private final ReactApplicationContext context;
    private Boolean executing = false;
    private USSDController ussdApi;

    @SuppressLint("MissingPermission")
    USSD(ReactApplicationContext context) {
        super(context);
        this.context = context;
        this.ussdApi = USSDController.getInstance(context);
        map.put("KEY_LOGIN", new HashSet<>(Arrays.asList("espere", "waiting", "loading", "esperando")));
        map.put("KEY_ERROR", new HashSet<>(Arrays.asList("problema", "problem", "error", "null")));

    }

    @Override
    @NonNull
    public String getName() {
        return "USSD";
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    public void verifySIM(Promise promise) {
        getSIMCount();
        SubscriptionManager subscriptionManager = (SubscriptionManager) context
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        for (int i = 0; i < SIMCount; i++) {
            if (subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(i) == null)
                promise.resolve(true);
            else
                promise.resolve(false);
        }
    }

    private void getSIMCount() {
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        @SuppressLint("MissingPermission")
        List<PhoneAccountHandle> phoneAccountHandleList = telecomManager.getCallCapablePhoneAccounts();
        SIMCount = phoneAccountHandleList.size();
    }

    @ReactMethod
    public void getSIMCount(Promise promise) {
        if (SIMCount == 0)
            getSIMCount();
        promise.resolve(SIMCount);
    }

    @ReactMethod
    public void isExecuting(Promise promise) {
        promise.resolve(executing);
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    public void getCarrier(int sim, Promise promise) {
        if (sim >= SIMCount)
            throw new Error("Invalid SIM ID entered");
        if (SIMManagers[0] == null) {
            SubscriptionManager subscriptionManager = (SubscriptionManager) context
                    .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            for (int i = 0; i < SIMCount; i++) {
                TelephonyManager telephonyManager = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                SIMManagers[i] = telephonyManager.createForSubscriptionId(
                        subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(i).getSubscriptionId());
            }
        }
        promise.resolve(SIMManagers[sim].getSimOperatorName());
    }

    @ReactMethod
    public void executeUSSD(String ussd, int sim, ReadableArray navigationArray, Promise promise) {
        ArrayList<Object> navigationObject = navigationArray.toArrayList();
        ArrayList<String> navigation = new ArrayList<>();
        for (Object o : navigationObject) {
            navigation.add((String) o);
        }
        ussdApi.verifyAccesibilityAccess(context.getCurrentActivity());
        ussdApi.verifyOverLay(context.getCurrentActivity());
        if (executing)
            promise.reject(new Error("A USSD code is already being executed"));
        executing = true;
        ussdApi.callUSSDInvoke(ussd, sim, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                // message has the response string data
                if (!navigation.isEmpty()) {
                    String nextCode = navigation.remove(0);

                    ussdApi.send(nextCode, new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            // message has the response string data from USSD
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }
                            if (!navigation.isEmpty())
                                navigate(navigation, promise);
                            else {
                                ussdApi.cancel();
                                executing = false;
                                try {
                                    Thread.sleep(100);

                                } catch (InterruptedException e) {

                                }
                                promise.resolve(message);
                            }
                        }
                    });
                } else {
                    ussdApi.cancel();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {

                    }
                    executing = false;
                    promise.resolve(message);
                }

            }

            @Override
            public void over(String message) {
                executing = false;
                promise.resolve(message);
            }
        });
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    public void sendSMSQuery(int sim, String destination, String message, Promise promise) {
        Settings sendSettings = new Settings();
        SubscriptionManager subscriptionManager = (SubscriptionManager) context
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        sendSettings.setSubscriptionId(
                subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(sim).getSubscriptionId());
        Transaction sendTransaction = new Transaction(context, sendSettings);
        Message mMessage = new Message(message, destination);
        SmsRadar.initializeSmsRadarService(context, new SmsListener() {

            @Override
            public void onSmsSent(Sms sms) {

            }

            @Override
            public void onSmsReceived(Sms sms) {
                SmsRadar.stopSmsRadarService(context);
                promise.resolve(sms.getMsg());
            }
        });
        sendTransaction.sendNewMessage(mMessage, Transaction.NO_THREAD_ID);
    }

    @SuppressLint("MissingPermission")
    public void sendSMS(int sim, String destination, String message, Promise promise) {
        Settings sendSettings = new Settings();
        SubscriptionManager subscriptionManager = (SubscriptionManager) context
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        sendSettings.setSubscriptionId(
                subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(sim).getSubscriptionId());
        Transaction sendTransaction = new Transaction(context, sendSettings);
        Message mMessage = new Message(message, destination);
        sendTransaction.sendNewMessage(mMessage, Transaction.NO_THREAD_ID);
    }

    @SuppressLint("MissingPermission")
    public void getSIMCards() {
        getSIMCount();
        int count = 0;
        SubscriptionManager subscriptionManager = (SubscriptionManager) context
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        for (int i = 0;; i++) {
            if (count == SIMCount)
                break;
            if (subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(i) == null)
                continue;
            count++;
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String carrier = telephonyManager.getSimOperatorName();
        }
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    public void getOffers(int sim, String code, String regex, String nextCode, Promise promise) {
        Pattern RegExp = Pattern.compile(regex);
        String acc = "";
        ussdApi.verifyAccesibilityAccess(context.getCurrentActivity());
        ussdApi.verifyOverLay(context.getCurrentActivity());
        if (executing)
            promise.reject(new Error("A USSD code is already being executed"));
        executing = true;
        ussdApi.callUSSDInvoke(code, sim, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                navigate(acc, message, nextCode, RegExp, promise);
                // message has the response string data
            }

            @Override
            public void over(String message) {
                executing = false;
                promise.resolve(message);
            }
        });
    }

    private void navigate(String acc, String message, String nextCode, Pattern RegExp, Promise promise) {
        acc += '\n' + message;
        boolean next = RegExp.matcher(message).find();
        if (next) {
            String finalAcc = acc;
            ussdApi.send(nextCode, new USSDController.CallbackMessage() {
                @Override
                public void responseMessage(String message) {
                    navigate(finalAcc, message, nextCode, RegExp, promise);
                }
            });
        } else {
            ussdApi.cancel();
            executing = false;
            promise.resolve(acc);
        }

    }

    private void navigate(ArrayList<String> nextCodes, Promise promise) {
        String nextCode = nextCodes.remove(0);
        ussdApi.send(nextCode, new USSDController.CallbackMessage() {
            @Override
            public void responseMessage(String message) {
                if (nextCodes.isEmpty()) {
                    ussdApi.cancel();
                    executing = false;
                    promise.resolve(message);
                } else
                    navigate(nextCodes, promise);
            }
        });

    }
}