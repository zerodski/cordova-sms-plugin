package com.cordova.plugins.sms;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.webkit.MimeTypeMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import android.os.Environment;
import android.os.StrictMode;
import android.telephony.SmsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;


import com.example.android.R;
import com.example.android.mmslib.ContentType;
import com.example.android.mmslib.InvalidHeaderValueException;
import com.example.android.mmslib.pdu.CharacterSets;
import com.example.android.mmslib.pdu.EncodedStringValue;
import com.example.android.mmslib.pdu.GenericPdu;
import com.example.android.mmslib.pdu.PduBody;
import com.example.android.mmslib.pdu.PduComposer;
import com.example.android.mmslib.pdu.PduHeaders;
import com.example.android.mmslib.pdu.PduParser;
import com.example.android.mmslib.pdu.PduPart;
import com.example.android.mmslib.pdu.RetrieveConf;
import com.example.android.mmslib.pdu.SendConf;
import com.example.android.mmslib.pdu.SendReq;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;

public class Sms extends CordovaPlugin {

    public final String ACTION_SEND_SMS = "send";

    public final String ACTION_HAS_PERMISSION = "has_permission";

    public final String ACTION_REQUEST_PERMISSION = "request_permission";

    private static final String INTENT_FILTER_SMS_SENT = "SMS_SENT";

    private static final int SEND_SMS_REQ_CODE = 0;

    private static final int REQUEST_PERMISSION_REQ_CODE = 1;

    private CallbackContext callbackContext;

    private JSONArray args;

	private File mSendFile;
	private Random mRandom = new Random();

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.args = args;
        switch (action) {
            case ACTION_SEND_SMS:
                boolean isIntent = false;
                try {
                    isIntent = args.getString(2).equalsIgnoreCase("INTENT");
                } catch (NullPointerException npe) {
                    // It might throw a NPE, but it doesn't matter.
                }
                if (isIntent || hasPermission()) {
                    sendSMS();
                } else {
                    requestPermission(SEND_SMS_REQ_CODE);
                }
                return true;
            case ACTION_HAS_PERMISSION:
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasPermission()));
                return true;
            case ACTION_REQUEST_PERMISSION:
                requestPermission(REQUEST_PERMISSION_REQ_CODE);
                return true;
        }
        return false;
    }

    private boolean hasPermission() {
        return cordova.hasPermission(android.Manifest.permission.SEND_SMS) && cordova.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
		//&& cordova.hasPermission(android.Manifest.permission.RECEIVE_SMS)
		//&& cordova.hasPermission(android.Manifest.permission.RECEIVE_MMS)
		//&& cordova.hasPermission(android.Manifest.permission.WRITE_SMS);
		//&& cordova.hasPermission(android.Manifest.permission.READ_SMS)
		//&& cordova.hasPermission(android.Manifest.permission.READ_PHONE_STATE);
    }

    private void requestPermission(int requestCode) {
        String[] permissions = {android.Manifest.permission.SEND_SMS, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
        cordova.requestPermissions(this, requestCode, permissions);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "User has denied permission"));
                return;
            }
        }
        if (requestCode == SEND_SMS_REQ_CODE) {
            sendSMS();
            return;
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
    }

    private void sendSMS() {
        cordova.getThreadPool().execute(() -> {
            try {
                String separator = ";";
                if (Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
                    separator = ",";
                }
                String phoneNumber = args.getJSONArray(0).join(separator).replace("\"", "");
                String message = args.getString(1);
                String image = args.getString(2);
                String method = args.getString(3);
                boolean replaceLineBreaks = Boolean.parseBoolean(args.getString(4));

                // replacing \n by new line if the parameter replaceLineBreaks is set to true
                if (replaceLineBreaks) {
                    message = message.replace("\\n", System.getProperty("line.separator"));
                }
                if (!checkSupport()) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "SMS not supported on this platform"));
                    return;
                }
                if (method.equalsIgnoreCase("INTENT")) {
                    //invokeSMSIntent(phoneNumber, message, image);
					sendMessage(phoneNumber, '', message, image);
                    // always passes success back to the app
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                } else {
				    send(callbackContext, phoneNumber, message);					
                }
            } catch (JSONException ex) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
            }
        });
    }

    private boolean checkSupport() {
        Activity ctx = this.cordova.getActivity();
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

	
    private void invokeSMSIntentNoImage(String phoneNumber, String message) {
        Intent sendIntent;
        sendIntent = new Intent(Intent.ACTION_VIEW);
        sendIntent.putExtra("sms_body", message);
        sendIntent.putExtra("address", phoneNumber);
        sendIntent.setData(Uri.parse("smsto:" + Uri.encode(phoneNumber)));
        this.cordova.getActivity().startActivity(sendIntent);
    }


    @SuppressLint("NewApi")
    private void invokeSMSIntent(String phoneNumber, String message, String imageFile) {
        if (imageFile.equals("")) {
            invokeSMSIntentNoImage(phoneNumber, message);
            return;
        }

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        String imageDataBytes = imageFile.substring(imageFile.indexOf(",")+1);
              
        byte[] decodedString = Base64.getDecoder().decode(imageDataBytes);
        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        String saveFilePath = cordova.getContext().getExternalCacheDir()+"";
        File dir = new File(saveFilePath);


        if (!dir.exists()) {
            dir.mkdirs();
        }
		
        String imageFileName = "selfie" + java.util.UUID.randomUUID().toString() + ".png";

        File file = new File(dir, imageFileName);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(file);
            decodedByte.compress(Bitmap.CompressFormat.PNG, 40, fOut);
            fOut.flush();
            fOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

		Intent sendIntent;
        sendIntent = new Intent(Intent.ACTION_SEND);
		//sendIntent.setClassName("com.android.mms","com.android.mms.ui.ComposeMessageActivity");

        sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(saveFilePath + "/" + imageFileName)));
		//String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(saveFilePath + "/" + imageFileName));
        //sendIntent.setType(mimeType);
		sendIntent.setType("image/*");
		sendIntent.putExtra("sms_body", message);
        sendIntent.putExtra("address", phoneNumber);
		
        this.cordova.getActivity().startActivity(sendIntent);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void send(final CallbackContext callbackContext, String phoneNumber, String message) {
        SmsManager manager = SmsManager.getDefault();
        final ArrayList<String> parts = manager.divideMessage(message);

        final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

            boolean anyError = false; //use to detect if one of the parts failed
            int partsCount = parts.size(); //number of parts to send

            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case SmsManager.STATUS_ON_ICC_SENT:
                    case Activity.RESULT_OK:
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        anyError = true;
                        break;
                }
                // trigger the callback only when all the parts have been sent
                partsCount--;
                if (partsCount == 0) {
                    if (anyError) {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
                    } else {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                    }
                    cordova.getActivity().unregisterReceiver(this);
                }
            }
        };

        // randomize the intent filter action to avoid using the same receiver
        String intentFilterAction = INTENT_FILTER_SMS_SENT + java.util.UUID.randomUUID().toString();
        this.cordova.getActivity().registerReceiver(broadcastReceiver, new IntentFilter(intentFilterAction));

        PendingIntent sentIntent = PendingIntent.getBroadcast(this.cordova.getActivity(), 0, new Intent(intentFilterAction), 0);

        // depending on the number of parts we send a text message or multi parts
        if (parts.size() > 1) {
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                sentIntents.add(sentIntent);
            }
			manager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null);
        } else {
            manager.sendTextMessage(phoneNumber, null, message, sentIntent, null);
        }
    }


	private void sendMessage(final String recipients, final String subject, final String text, String imageFile) {
        Log.d(TAG, "Sending");
        final String fileName = "send." + String.valueOf(Math.abs(mRandom.nextLong())) + ".dat";
        mSendFile = new File(getCacheDir(), fileName);

        // Making RPC call in non-UI thread
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap imageToSend = null;

				String imageDataBytes = imageFile.substring(imageFile.indexOf(",")+1);
				byte[] decodedString = Base64.getDecoder().decode(imageDataBytes);
				Bitmap imageToSend = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);


                final byte[] pdu = buildPdu(Sms.this, recipients, subject, text, imageToSend);
                Uri writerUri = (new Uri.Builder())
                        .authority("com.example.android.apis.os.MmsFileProvider")
                        .path(fileName)
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .build();
                
                FileOutputStream writer = null;
                Uri contentUri = null;
                try {
                    writer = new FileOutputStream(mSendFile);
                    writer.write(pdu);
                    contentUri = writerUri;
                } catch (final IOException e) {
                    Log.e(TAG, "Error writing send file", e);
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                        }
                    }
                }

                if (contentUri != null) {
                    SmsManager.getDefault().sendMultimediaMessage(getApplicationContext(),
                            contentUri, null/*locationUrl*/, null/*configOverrides*/,
                            null);
                } else {
                    Log.e(TAG, "Error writing sending Mms");                    
                }
            }
        });
    }

	private static byte[] buildPdu(Context context, String recipients, String subject, String text, Bitmap imageToSend) {
        final SendReq req = new SendReq();
        // From, per spec
        final String lineNumber = getSimNumber(context);
        if (!TextUtils.isEmpty(lineNumber)) {
            req.setFrom(new EncodedStringValue(lineNumber));
        }
        // To
        EncodedStringValue[] encodedNumbers =
                EncodedStringValue.encodeStrings(recipients.split(" "));
        if (encodedNumbers != null) {
            req.setTo(encodedNumbers);
        }
        // Subject
        if (!TextUtils.isEmpty(subject)) {
            req.setSubject(new EncodedStringValue(subject));
        }
        // Date
        req.setDate(System.currentTimeMillis() / 1000);
        // Body
        PduBody body = new PduBody();
        int size = 0;

        // Add text part. Always add a smil part for compatibility, without it there
        // may be issues on some carriers/client apps
        size += addTextPart(body, text, true/* add text smil */);

        // Add image part
        if (imageToSend != null)
            size += addImagePart(body, imageToSend);

        req.setBody(body);
        // Message size
        req.setMessageSize(size);
        // Message class
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        // Expiry
        req.setExpiry(DEFAULT_EXPIRY_TIME);
        try {
            // Priority
            req.setPriority(DEFAULT_PRIORITY);
            // Delivery report
            req.setDeliveryReport(PduHeaders.VALUE_NO);
            // Read report
            req.setReadReport(PduHeaders.VALUE_NO);
        } catch (InvalidHeaderValueException e) {
        }

        return new PduComposer(context, req).make();
    }

    private static int addTextPart(PduBody pb, String message, boolean addTextSmil) {
        final PduPart part = new PduPart();
        // Set Charset if it's a text media.
        part.setCharset(CharacterSets.UTF_8);
        // Set Content-Type.
        part.setContentType(ContentType.TEXT_PLAIN.getBytes());
        // Set Content-Location.
        part.setContentLocation(TEXT_PART_FILENAME.getBytes());
        int index = TEXT_PART_FILENAME.lastIndexOf(".");
        String contentId = (index == -1) ? TEXT_PART_FILENAME
                : TEXT_PART_FILENAME.substring(0, index);
        part.setContentId(contentId.getBytes());
        part.setData(message.getBytes());
        pb.addPart(part);
        if (addTextSmil) {
            final String smil = String.format(sSmilText, TEXT_PART_FILENAME);
            addSmilPart(pb, smil);
        }
        return part.getData().length;
    }

    private static int addImagePart(PduBody pb, Bitmap imageToSend) {
        final PduPart part = new PduPart();

        //add image part
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        imageToSend.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] imageBytes = stream.toByteArray();
        imageToSend.recycle();

        part.setName(("image_" + System.currentTimeMillis()).getBytes());
        part.setContentType("image/jpeg".getBytes());
        part.setData(imageBytes);

        pb.addPart(part);
        return part.getData().length;
    }

	private static void addSmilPart(PduBody pb, String smil) {
        final PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(smil.getBytes());
        pb.addPart(0, smilPart);
    }

}
