package org.openmrs.mobile.bluetooth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by federico on 17/10/2017.
 */

public class PressureMonitorUtils {
    private static final String TAG ="PressureMonitorUtils.java" ;
    private static final String PREFS_NAME ="pressPref" ;
    private static final String KEY_PASS ="keypass" ;
    private static final String KEY_ACCOUNT ="keyaccount" ;

    public static byte[] getoffsetTime() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date();
        Date now = new Date();
        try {
            date = fmt.parse("2010-01-01 00:00:00");
            //now= fmt.parse("2015-11-01 13:00:00");

        } catch (ParseException e) {
            e.printStackTrace();
        }
        long offset = now.getTime() - date.getTime();
        long offsetSecond = offset / 1000;
        Log.d(TAG, "offset: " + offsetSecond);
        byte[] aux = longToBytes(offsetSecond, 4);;
        return aux;

    }

    public static byte[] longToBytes(long l, int size) {
        byte[] result = new byte[size];
        for (int i = size - 1; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    public static void savePassword(Context ctx,byte [] password){
        String pwd= Base64.encodeToString(password,Base64.DEFAULT);
        SharedPreferences settings=ctx.getSharedPreferences(PREFS_NAME,0);
        SharedPreferences.Editor editor= settings.edit();
        editor.putString(KEY_PASS,pwd);
        editor.commit();
    }
    public static byte [] getPassword(Context ctx){
        SharedPreferences settings=ctx.getSharedPreferences(PREFS_NAME,0);
        String pwd=settings.getString(KEY_PASS,"");
        byte [] password= Base64.decode(pwd,Base64.DEFAULT);
        return password;
    }
    public static void saveAccountID(Context ctx,byte [] accountID){
        String str= Base64.encodeToString(accountID,Base64.DEFAULT);
        SharedPreferences settings=ctx.getSharedPreferences(PREFS_NAME,0);
        SharedPreferences.Editor editor= settings.edit();
        editor.putString(KEY_ACCOUNT,str);
        editor.commit();
    }
    public static byte [] getAccountID(Context ctx){
        SharedPreferences settings=ctx.getSharedPreferences(PREFS_NAME,0);
        String str=settings.getString(KEY_ACCOUNT,"");
        byte [] arrayByte= Base64.decode(str,Base64.DEFAULT);
        return arrayByte;
    }
    public static boolean checkAccountID(Context ctx, String deviceName){
        byte[] accountID=getAccountID(ctx);
        if (accountID.length!=4)
            return false;
        String savedID = String.format("%02x", accountID[0]) +
                String.format("%02x", accountID[1]) +
                String.format("%02x", accountID[2]) +
                String.format("%02x", accountID[3]);
        Log.d(TAG, "account:" + savedID);
        String receivedID=deviceName.substring(6,14);
        Log.d(TAG, "accountScan:"+receivedID);
        return receivedID.equalsIgnoreCase(savedID);
    }
    public static String getAccountIDText(Context ctx){
        byte[] accountID=getAccountID(ctx);
        if (accountID.length!=4)
            return new String();
        String savedID = String.format("%02x", accountID[0]) +
                String.format("%02x", accountID[1]) +
                String.format("%02x", accountID[2]) +
                String.format("%02x", accountID[3]);
        return savedID;
    }
    public static void clearAccountID(Context ctx) {
        SharedPreferences shared=ctx.getSharedPreferences(PREFS_NAME,0);
        SharedPreferences.Editor ed=shared.edit();
        ed.remove(KEY_ACCOUNT);
        ed.commit();
    }

}
