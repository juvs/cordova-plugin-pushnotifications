package org.apache.cordova.notification;

import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class PushNotification extends CordovaPlugin {
	private static String TAG = "PushNotificationsPlugin";

	public static final String REGISTER = "registerForNotifications";
	public static final String UNREGISTER = "unregisterForNotifications";
	public static final String ON_DEVICE_READY = "onDeviceReady";

	private static final String PREFERENCES = "com.google.android.gcm";
	private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
	private static final String PROPERTY_REG_ID = "registration_id";
	private static final String PROPERTY_APP_VERSION = "appVersion";

	private GoogleCloudMessaging gcm;
	private String regid;
	private String showedNotificationId = "";
	
	private static String gSenderID;

	public PushNotification() {
		super();
	}

	/**
	 * Gets the application context from cordova's main activity.
	 * @return the application context
	 */
	private Context getApplicationContext() {
		return this.cordova.getActivity().getApplicationContext();
	}	
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		Log.d(TAG, "onActivityResult");
		super.onActivityResult(requestCode, resultCode, intent);
	}

	@Override
	public void onResume(boolean multitasking) {
		Log.d(TAG, "onResume");
		super.onResume(multitasking);
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		Log.d(TAG, "initialize");
		super.initialize(cordova, webView);
	}

	@Override
	public Object onMessage(String id, Object data) {
		Log.d(TAG, "onMessage");
		return super.onMessage(id, data);
	}

	@Override
	public void onPause(boolean multitasking) {
		Log.d(TAG, "onPause");
		super.onPause(multitasking);
	}

	/**
	 * Called when the activity receives a new intent.
	 */
	@Override
	public void onNewIntent(Intent intent) {
		Log.d(TAG, "onNewIntent");
		super.onNewIntent(intent);
		checkMessage(intent, false);
	}

	/**
	 * Executes the request and returns PluginResult.
	 * 
	 * @param action
	 *            The action to execute.
	 * @param args
	 *            JSONArry of arguments for the plugin.
	 * @param callbackId
	 *            The callback id used when calling back into JavaScript.
	 * @return A PluginResult object with a status and message.
	 */
	@Override
	public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
		boolean result = false;

		Log.d(TAG, "execute: action=" + action);

		if (ON_DEVICE_READY.equals(action)) {
			checkMessage(cordova.getActivity().getIntent(), true);
			return true;
		}

		if (REGISTER.equals(action)) {
			try {
				JSONObject jo = data.getJSONObject(0);
				Log.v(TAG, "execute: jo=" + jo.toString());

				gSenderID = (String) jo.get("senderID");

				Log.v(TAG, "execute: senderID=" + gSenderID);
				registerForNotifications(gSenderID);
				result = true;
				callbackContext.success();
			} catch (JSONException e) {
				Log.e(TAG, "execute: Got JSON Exception " + e.getMessage());
				result = false;
				callbackContext.error(e.getMessage());
			}
		}

		if (UNREGISTER.equals(action)) {
			unregisterForNotifications();
			result = true;
			callbackContext.success();
		}
		Log.d(TAG, "Invalid action : " + action);
		return result;
	}

	private void checkMessage(Intent intent, boolean withDelay) {
		if (intent != null) {
			Log.d(TAG, "PUSH_RECEIVE_EVENT:" + intent.hasExtra("PUSH_RECEIVE_EVENT"));
			if (intent.hasExtra("PUSH_RECEIVE_EVENT") && !this.showedNotificationId.equals(intent.getStringExtra("id"))) {
				Log.d(TAG, "Processing notification with id : " + intent.getStringExtra("collapse_key"));
				JSONObject data = new JSONObject();
				String url = intent.getStringExtra("siteUrl");
				try {
					data.put("title", intent.getStringExtra("title"));
					data.put("msg", intent.getStringExtra("msg"));
					if (null != url && !url.trim().equals("")) {
						data.put("url", url);
					}
					if (withDelay) {
						data.put("delay", true);
					}
				} catch (JSONException e) {
					Log.e("notifyMovement", e.getMessage());
				}
				String message = data.toString();
				Log.d(TAG, "message is: " + message);
				final String jsStatement = String.format("window.plugins.pushNotification.notificationCallback(%s);",
						message);
				cordova.getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						webView.loadUrl("javascript:" + jsStatement);
					}
				});
				this.showedNotificationId = intent.getStringExtra("id");
				intent.removeExtra("collapse_key");
				intent.removeExtra("PUSH_RECEIVE_EVENT");
			}
		}

	}

	private void unregisterForNotifications() {
		gcm = GoogleCloudMessaging.getInstance(this.cordova.getActivity());
		try {
			gcm.unregister();
		} catch (IOException ex) {
			Log.e(TAG, "Error unregistering device.", ex);
		}
		// Always delete the GCM token stored.
		final SharedPreferences prefs = getGCMPreferences(getApplicationContext());
		SharedPreferences.Editor editor = prefs.edit();
		editor.remove(PROPERTY_REG_ID);
		editor.commit();
		Log.d(TAG, "This device was unregister for notifications.");
	}

	private void registerForNotifications(String senderID) {
		// Check device for Play Services APK. If check succeeds, proceed with
		// GCM registration.
		Context context = getApplicationContext();
		if (checkPlayServices()) {
			gcm = GoogleCloudMessaging.getInstance(context);
			regid = getRegistrationId(getApplicationContext());

			if (regid.equals("")) {
				registerInBackground(context, senderID);
			} else {
				Log.d(TAG, "This device is already register for notifications.");
				sendRegistrationIdToJavascript();
			}
		} else {
			Log.e(TAG, "No valid Google Play Services APK found.");
		}
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it doesn't, display a dialog that allows
	 * users to download the APK from the Google Play Store or enable it in the device's system settings.
	 */
	private boolean checkPlayServices() {
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, this.cordova.getActivity(),
						PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else {
				Log.d(TAG, "This device is not supported for Google Play Services.");
			}
			return false;
		}
		return true;
	}

	/**
	 * Gets the current registration ID for application on GCM service.
	 * <p>
	 * If result is empty, the app needs to register.
	 * 
	 * @return registration ID, or empty string if there is no existing registration ID.
	 */
	private String getRegistrationId(Context context) {
		final SharedPreferences prefs = getGCMPreferences(context);
		String registrationId = prefs.getString(PROPERTY_REG_ID, "");
		if (registrationId.equals("")) {
			Log.d(TAG, "Registration not found.");
			return "";
		}
		// Check if app was updated; if so, it must clear the registration ID
		// since the existing regID is not guaranteed to work with the new
		// app version.
		String registeredVersion = prefs.getString(PROPERTY_APP_VERSION, "0.0.0");
		String currentVersion = getAppVersion(context);
		if (!registeredVersion.equals(currentVersion)) {
			Log.d(TAG, "App version changed.");
			return "";
		}
		return registrationId;
	}

	/**
	 * @return Application's {@code SharedPreferences}.
	 */
	private SharedPreferences getGCMPreferences(Context context) {
		// This sample app persists the registration ID in shared preferences, but
		// how you store the regID in your app is up to you.
		return getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
	}

	/**
	 * @return Application's version code from the {@code PackageManager}.
	 */
	private static String getAppVersion(Context context) {
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return packageInfo.versionName;
		} catch (NameNotFoundException e) {
			// should never happen
			throw new RuntimeException("Could not get package name: " + e);
		}
	}

	/**
	 * Registers the application with GCM servers asynchronously.
	 * <p>
	 * Stores the registration ID and app versionCode in the application's shared preferences.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void registerInBackground(final Context context, final String senderID) {
		Log.d(TAG, "Requesting for register device for notifications services...");
		new AsyncTask() {
			protected Object doInBackground(final Object... params) {
				try {
					if (gcm == null) {
						gcm = GoogleCloudMessaging.getInstance(context);
					}
					regid = gcm.register(senderID);
					Log.d(TAG, "Device was registered, token is : " + regid);

					// Send the registration ID to your server
					sendRegistrationIdToJavascript();

					// Persist the regID - no need to register again.
					storeRegistrationId(context, regid);
				} catch (IOException ex) {
					Log.e(TAG, "Error registering device.", ex);
					// If there is an error, don't just keep trying to register.
					// Require the user to click a button again, or perform
					// exponential back-off.
				}
				return true;
			}

		}.execute(null, null, null);

	}

	/**
	 * Stores the registration ID and app versionCode in the application's {@code SharedPreferences}.
	 * 
	 * @param context
	 *            application's context.
	 * @param regId
	 *            registration ID
	 */
	private void storeRegistrationId(Context context, String regId) {
		final SharedPreferences prefs = getGCMPreferences(context);
		String appVersion = getAppVersion(context);
		Log.i(TAG, "Saving token on app version " + appVersion);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_REG_ID, regId);
		editor.putString(PROPERTY_APP_VERSION, appVersion);
		editor.commit();
	}

	/**
	 * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP or CCS to send messages to your app.
	 * Not needed for this demo since the device sends upstream messages to a server that echoes back the message using
	 * the 'from' address in the message.
	 */
	// private void sendRegistrationIdToBackend() {
	// String js = String.format("javascript:sp.notifyService.registerToken('%s');", regid);
	// this.webView.sendJavascript(js);
	// }

	private void sendRegistrationIdToJavascript() {
		Log.i(TAG, "Send registration token to JavaScript");
		JSONObject data = new JSONObject();
		try {
			data.put("platform", "GCM");
			data.put("token", regid);
		} catch (JSONException e) {
			Log.e(TAG, e.getMessage());
		}
		String message = data.toString();
		final String jsStatement = String
				.format("window.plugins.pushNotification.registerDeviceCallback(%s);", message);
		Log.i(TAG, jsStatement);
		cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				webView.loadUrl("javascript:" + jsStatement);
			}
		});
	}
}
