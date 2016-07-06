/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package uk.co.onefile.nomadionic.audiocapture;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.os.Build;
import android.util.Log;
import android.content.Context;
import android.content.Intent;

import org.apache.cordova.file.FileUtils;
import org.apache.cordova.file.LocalFilesystemURL;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaHttpAuthHandler;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;

import android.util.Log;
import android.content.Intent;

public class CDVAudioRecorder extends CordovaPlugin {

	private final String TAG = "CDVAudioRecorder";
	private final int AUDIO_RECORDER_REQUEST_CODE = 1001;
	private CallbackContext callbackContextWithResult;

	@Override
	protected void pluginInitialize() {
		super.pluginInitialize();
		Log.i(TAG, "pluginInitialize()");
	}

	@Override
	public boolean execute(String action, final JSONArray data, final CallbackContext callbackContext) throws JSONException {
		Log.i(TAG, "execute() called - checking action equals audioRecorder");
		Log.i(TAG, action);
		if (action.equals("audioRecorder")) {
			callbackContextWithResult = callbackContext;
			cordova.setActivityResultCallback(this);
			cordova.getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Context context = cordova.getActivity().getApplicationContext();
					// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					// Pass information to the activity.
					Intent intent = new Intent(context, AudioRecorder.class);
					intent.putExtra("entryDataString", data.optJSONObject(0).toString());
					// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
					cordova.getActivity().startActivityForResult(intent, AUDIO_RECORDER_REQUEST_CODE);
				}
			});
			return true;
		}
		Log.i(TAG, "action not equal to audioRecorder - finishing");
		return false;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// Only deal with any requests from our callback.
		if(requestCode == AUDIO_RECORDER_REQUEST_CODE) {
			if(resultCode == cordova.getActivity().RESULT_OK) {
				Log.i(TAG, "plugin - onActivityResult - RESULT_OK");
				// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				// Returned information from the activity called above
				String data = new String();
				Bundle extras = intent.getExtras();
				if (extras != null) {
					try {
						JSONObject jsonData = new JSONObject();
						JSONObject fileDetails = new JSONObject();
						fileDetails.put("fullPath", extras.getString("filePath"));
						fileDetails.put("localURL", extras.getString("localURL"));
						fileDetails.put("name", extras.getString("fileName"));
						fileDetails.put("ext", extras.getString("fileExt"));
						fileDetails.put("size", extras.getString("fileSize"));
						fileDetails.put("type", extras.getString("fileType"));
						jsonData.put("fileDetails", fileDetails);
						jsonData.put("status", 2);
						callbackContextWithResult.success(jsonData);
					}
					catch (JSONException e)
					{
						callbackContextWithResult.error(e.getMessage());
						e.printStackTrace();
					}
				}
				// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
			}
			callbackContextWithResult.error("Error recording");
		}
	}

	@Override
	public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext)
	{
		Log.i(TAG, "onRestoreStateForActivityResult");
	}

	@Override
	public Object onMessage(String id, Object data) {
		Log.i(TAG, "onMessage");
		return null;
	}
}
