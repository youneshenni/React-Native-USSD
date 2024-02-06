package com.siraj.topup.USSD;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.siraj.topup.R;

public class SIM {
    // Main attributes
    private String number;
    private float balance;
    private int operator;

    // Mobile Operators list
    private static final int MOBILIS = 0;
    private static final int DJEZZY = 1;
    private static final int OOREDOO = 2;

    TelephonyManager manager;

    SIM(TelephonyManager manager) {
        XmlResourceParser USSDParser = Resources.getSystem().getXml(R.xml.ussd);
        this.manager = manager;
        // Get Mobile Operator
        String operator = manager.getSimOperatorName();
        Log.v("USSD", operator);
        switch (operator) {
            case "Mobilis":
                this.operator = MOBILIS;
            case "Djezzy":
                this.operator = DJEZZY;
            case "Ooredoo":
                this.operator = OOREDOO;
        }

    }
}
