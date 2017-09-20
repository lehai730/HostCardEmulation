/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.example.android.cardemulation;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import com.example.android.common.logger.Log;

import java.util.Arrays;

/**
 * This is a sample APDU Service which demonstrates how to interface with the card emulation support
 * added in Android 4.4, KitKat.
 *
 * <p>This sample replies to any requests sent with the string "Hello World". In real-world
 * situations, you would need to modify this code to implement your desired communication
 * protocol.
 *
 * <p>This sample will be invoked for any terminals selecting AIDs of 0xF11111111, 0xF22222222, or
 * 0xF33333333. See src/main/res/xml/aid_list.xml for more details.
 *
 * <p class="note">Note: This is a low-level interface. Unlike the NdefMessage many developers
 * are familiar with for implementing Android Beam in apps, card emulation only provides a
 * byte-array based communication channel. It is left to developers to implement higher level
 * protocol support as needed.
 */
public class CardService extends HostApduService {
    private static final String TAG = "CardService";

    /* NDEF message definition
     * 0x00, 0x0B,                 NDEF message size
     * 0xD1,                       NDEF RECORD HEADER MB/ME/CF/1/IL/TNF
     * 0x01,                       TYPE LENGTH
     * 0x07,                       PAYLOAD LENTGH
     * 'T', 0x54                   TYPE
     * 0x02,                       Language length
     * 'e', 'n',                   Language
     * 'T', 'e', 's', 't'          Text
     */
    private byte[] NDEF_MESSAGE = CreateNDEF();


    private static final byte[] T4T_NDEF_EMU_APP_Select = HexStringToByteArray("00A4040007D2760000850101");
    private static final byte[] T4T_NDEF_EMU_CC = HexStringToByteArray("000F2000FF00FF0406E10400FF00FF");
    private static final byte[] T4T_NDEF_EMU_CC_Select = HexStringToByteArray("00A4000C02E103");
    private static final byte[] T4T_NDEF_EMU_NDEF_Select = HexStringToByteArray("00A4000C02E104");
    private static final byte[] T4T_NDEF_EMU_Read = HexStringToByteArray("00B0");
    private static final byte[] SELECT_OK_SW = HexStringToByteArray("9000");
    private static final byte[] UNKNOWN_CMD_SW = HexStringToByteArray("0000");

    private enum NDEF_state {
        Ready,
        NDEF_Application_Selected,
        CC_Selected,
        NDEF_Selected
    }
    private static NDEF_state eT4T_NDEF_EMU_State = NDEF_state.Ready;

    /**
     * Called if the connection to the NFC card is lost, in order to let the application know the
     * cause for the disconnection (either a lost link, or another AID being selected by the
     * reader).
     *
     * @param reason Either DEACTIVATION_LINK_LOSS or DEACTIVATION_DESELECTED
     */
    @Override
    public void onDeactivated(int reason) { }

    /*
    * Checking if byte array a is equal to byte array b
    */
    private boolean IsEqual (byte[] a, byte[] b, int n) {
        for (int i=0; i<n; i++) {
            if(a[i] != b[i]) return false;
        }
        return true;
    }
    /**
     * This method will be called when a command APDU has been received from a remote device. A
     * response APDU can be provided directly by returning a byte-array in this method. In general
     * response APDUs must be sent as quickly as possible, given the fact that the user is likely
     * holding his device over an NFC reader when this method is called.
     *
     * <p class="note">If there are multiple services that have registered for the same AIDs in
     * their meta-data entry, you will only get called if the user has explicitly selected your
     * service, either as a default or just for the next tap.
     *
     * <p class="note">This method is running on the main thread of your application. If you
     * cannot return a response APDU immediately, return null and use the {@link
     * #sendResponseApdu(byte[])} method later.
     *
     * @param commandApdu The APDU that received from the remote device
     * @param extras A bundle containing extra data. May be null.
     * @return a byte-array containing the response APDU, or null if no response APDU can be sent
     * at this point.
     */
    // BEGIN_INCLUDE(processCommandApdu)
    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        byte[] Answer = UNKNOWN_CMD_SW;

        Log.i(TAG, "Received APDU: " + ByteArrayToHexString(commandApdu));

        if (IsEqual(T4T_NDEF_EMU_APP_Select, commandApdu, T4T_NDEF_EMU_APP_Select.length)) {
            eT4T_NDEF_EMU_State = NDEF_state.NDEF_Application_Selected;
            Answer = SELECT_OK_SW;
        }
        else if (IsEqual(T4T_NDEF_EMU_CC_Select, commandApdu, T4T_NDEF_EMU_CC_Select.length)) {
            if(eT4T_NDEF_EMU_State == NDEF_state.NDEF_Application_Selected) {
                eT4T_NDEF_EMU_State = NDEF_state.CC_Selected;
                Answer = SELECT_OK_SW;
            }
            else {
                eT4T_NDEF_EMU_State = NDEF_state.Ready;
            }
        }
        else if (IsEqual(T4T_NDEF_EMU_NDEF_Select, commandApdu, T4T_NDEF_EMU_NDEF_Select.length)) {
            eT4T_NDEF_EMU_State = NDEF_state.NDEF_Selected;
            Answer = SELECT_OK_SW;
        }
        else if (IsEqual(T4T_NDEF_EMU_Read, commandApdu, T4T_NDEF_EMU_Read.length)) {
            if(eT4T_NDEF_EMU_State == NDEF_state.CC_Selected)
            {
                Answer = ConcatArrays(T4T_NDEF_EMU_CC, SELECT_OK_SW);
            }
            else if (eT4T_NDEF_EMU_State == NDEF_state.NDEF_Selected)
            {
                int offset = (commandApdu[2] << 8) + commandApdu[3];//2 byte into decimal number
                int length = commandApdu[4];
                Log.i(TAG, "Reading NDEF file offset = " + offset + " length = " + length);
                byte[] temp = new byte[length];

                System.arraycopy(NDEF_MESSAGE, offset, temp, 0, temp.length);

                Log.i(TAG, "New NDEF Message " + ByteArrayToHexString(NDEF_MESSAGE));
                Answer = ConcatArrays(temp, SELECT_OK_SW);
            }
            else {
                eT4T_NDEF_EMU_State = NDEF_state.Ready;
            }
        }

        Log.i(TAG, "state = " + eT4T_NDEF_EMU_State);
        Log.i(TAG, "Returned APDU: " + ByteArrayToHexString(Answer));

        return Answer;
    }
    // END_INCLUDE(processCommandApdu)

    /**
     * Utility method to build an NDEF message based on account number retreived from account storage.
     *
     *
     * @return byte array, the NDEF message array that we are going to send.
     */
    public byte[] CreateNDEF(){

        String account = AccountStorage.GetAccount(this); // Getting text from type in field!!
        byte[] accountBytes = account.getBytes();


        int payloadLength = account.length() + 3;           // Length of account number
        int NDEFLength = account.length() + 7;
        String totalLength = Integer.toHexString(NDEFLength);
        Log.i(TAG, "Account Length Hex: " + totalLength);


        //byte[] NDEF_MESSAGE = HexStringToByteArray("000DD101095402656E313233343536");
        byte[] NDEF_MESSAGE1 = HexStringToByteArray("00");

        if (NDEFLength<=15){
            NDEF_MESSAGE1 = ConcatArrays(NDEF_MESSAGE1,HexStringToByteArray("0"+totalLength));
        }
        else{
            NDEF_MESSAGE1 = ConcatArrays(NDEF_MESSAGE1,HexStringToByteArray(totalLength));
        }
        // NDEF_MESSAGE1 is currently first 4 digits of the NDEF message
        NDEF_MESSAGE1 = ConcatArrays(NDEF_MESSAGE1,HexStringToByteArray("D101"));
        if (payloadLength<=15){
            NDEF_MESSAGE1 = ConcatArrays(NDEF_MESSAGE1,HexStringToByteArray("0"+Integer.toHexString(payloadLength)));
        }
        else{
            NDEF_MESSAGE1 = ConcatArrays(NDEF_MESSAGE1,HexStringToByteArray(Integer.toHexString(payloadLength)));
        }
        NDEF_MESSAGE1 = ConcatArrays(NDEF_MESSAGE1,HexStringToByteArray("5402656E"));
        NDEF_MESSAGE1 = ConcatArrays(NDEF_MESSAGE1,accountBytes);

        return NDEF_MESSAGE1;
    }

    /**
     * Utility method to convert a byte array to a hexadecimal string.
     *
     * @param bytes Bytes to convert
     * @return String, containing hexadecimal representation.
     */
    public static String ByteArrayToHexString(byte[] bytes) {
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2]; // Each byte has two hex characters (nibbles)
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF; // Cast bytes[j] to int, treating as unsigned value
            hexChars[j * 2] = hexArray[v >>> 4]; // Select hex character from upper nibble
            hexChars[j * 2 + 1] = hexArray[v & 0x0F]; // Select hex character from lower nibble
        }
        return new String(hexChars);
    }

    /**
     * Utility method to convert a hexadecimal string to a byte string.
     *
     * <p>Behavior with input strings containing non-hexadecimal characters is undefined.
     *
     * @param s String containing hexadecimal characters to convert
     * @return Byte array generated from input
     * @throws java.lang.IllegalArgumentException if input length is incorrect
     */
    public static byte[] HexStringToByteArray(String s) throws IllegalArgumentException {
        int len = s.length();
        if (len % 2 == 1) {
            throw new IllegalArgumentException("Hex string must have even number of characters");
        }
        byte[] data = new byte[len / 2]; // Allocate 1 byte per 2 hex characters
        for (int i = 0; i < len; i += 2) {
            // Convert each character into a integer (base-16), then bit-shift into place
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Utility method to concatenate two byte arrays.
     * @param first First array
     * @param rest Any remaining arrays
     * @return Concatenated copy of input arrays
     */
    public static byte[] ConcatArrays(byte[] first, byte[]... rest) {
        int totalLength = first.length;
        for (byte[] array : rest) {
            totalLength += array.length;
        }
        byte[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (byte[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
