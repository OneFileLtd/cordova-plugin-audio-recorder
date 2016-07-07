package uk.co.onefile.nomadionic.audiocapture;

import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;

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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.graphics.Paint.Style;

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

import android.os.Bundle;
import android.view.View;
import android.app.Activity;

// TODO: This needs putting back in for the plugin, I think the resources and project path don't equate to the same path.
import uk.co.onefile.nomadionic.R;

public class AudioRecorder extends Activity {

	private static final Integer STATE_NOT_SET = 0;
	private static final Integer STATE_RECORDING = 1;
	private static final Integer STATE_PAUSED = 2;
	private static final Integer STATE_STOPPED = 3;
	private static final Integer STATE_SAVE = 4;
	private static final Integer STATE_SAVING = 5;
	private static final Integer STATE_EXIT = 6;
	private static final Integer STATE_EXITING = 7;
	private static final Integer STATE_FINISHED_RECORDING = 8;

	private static final Integer STATE_AUDIO_TASK_NOT_SET = 0;
	private static final Integer STATE_AUDIO_TASK_STOPPED = 1;
	private static final Integer STATE_AUDIO_TASK_PAUSED = 2;
	private static final Integer STATE_AUDIO_TASK_RECORDING = 3;
	private static final Integer STATE_AUDIO_TASK_ERROR = 4;

	private Integer currentState = STATE_NOT_SET;
	private Integer currentState_AudioTask = STATE_AUDIO_TASK_NOT_SET;

	public static final Integer RECORDING_FREQUENCY = 11025;
	public String storageDirectory = Environment.getExternalStorageDirectory().getPath() + "/.audiorecorder/";

	private String TAG = "Audio Recorder";
	private Integer maxUpload = 0;
	private String audioOutputPath = "";
	private Integer pauseCount = -1;
	private String fileSizeErrorMsg = "";
	private long sizeSoFar = 50000;
	private long maxSize = 30000000;
	private LinearLayout savingLayout;
	private TextView recordingTime;
	private TextView recordingSize;
	private TextView maxRecordingSize;
	private AudioRecordingTask audioRecordingTask;
	private boolean audioRecordingNotStarted = true;
	private boolean audioHasBeenRecorded = false;
	private boolean audioIsBeingRecorded = false;
	private boolean errorOccured = false;
	private long mStartTime = 0L;
	private long mTotalTime = 0L;
	private String timeText = "00:00:00";
	private String sizeText = "Total recording size: 0.00 MB";
	private Handler mHandler = new Handler();
	private static final DecimalFormat df2 = new DecimalFormat("#,###,###,##0.00");
	private long uploadLimit = 0;
	
	private final String tempPart1StorageFilePath = storageDirectory + "temp_audio_";
	private final String tempStorageFilePath = storageDirectory + "temp_audio_0.pcm";
	private final String finalStorageFileName = "onefileaudio.wav";
	private final String finalStorageFilePath = storageDirectory + finalStorageFileName;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate()");
		setContentView(R.layout.activity_main);
		setTitle("Audio Recorder");

		if (savedInstanceState != null)
		{
			restoreSession(savedInstanceState);
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		Log.i(TAG, "onPause()");

		if (audioRecordingTask != null)
		{
			audioRecordingTask.pause();
			Log.i(TAG, "Pausing recording");
		}

		while (!audioRecordingNotStarted)
		{
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Log.i(TAG, "onResume()");
		setContentView(R.layout.activity_main);
		recordingTime = (TextView) findViewById(R.id.recordingTime);
		recordingSize = (TextView) findViewById(R.id.recordingSize);
		maxRecordingSize = (TextView) findViewById(R.id.maxSize);
		savingLayout = (LinearLayout) findViewById(R.id.savingLayout);
		recordingTime.setText(timeText);
		recordingSize.setText(sizeText);
		maxRecordingSize.setText("Max recording size: "+uploadLimit+" MB");
		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// PULL INFORMATION FROM THE CALLING PLUGIN ENTRY POINT
		String data = new String();
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if(extras != null)
			data = extras.getString("entryDataString"); // retrieve the data using keyName
		Log.i(TAG, data);
		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		checkExternalStorage();
		setUpButtons();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState)
	{
		Log.i(TAG, "onSaveInstanceState() Called");
		savedInstanceState.putInt("pauseCount", pauseCount);
		savedInstanceState.putLong("sizeSoFar", sizeSoFar);
		savedInstanceState.putBoolean("audioRecordingNotStarted", audioRecordingNotStarted);
		savedInstanceState.putBoolean("audioHasBeenRecorded", audioHasBeenRecorded);
		savedInstanceState.putString("timeText", timeText);
		savedInstanceState.putString("sizeText", sizeText);
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onBackPressed()
	{
		Log.i(TAG, "onBackPressed");
	}

	// Function that converst bytes to Megabytes
	public static float bytesToMeg(float bytes)
	{
		float megaByte = 1024L * 1024L;
		return (bytes / megaByte);
	}

	private final Runnable mUpdateTimeTask = new Runnable() {

		public void run()
		{
			Log.i(TAG, "Runnable mUpdateTimeTask()");
			long millis = System.currentTimeMillis() - mStartTime;
			int seconds = (int) (millis / 1000);
			int minutes = seconds / 60;
			int hours = seconds / 3600;
			seconds = seconds % 60;

			mTotalTime = millis;
			double size = 0;

			if (audioRecordingTask != null)
			{
				float fsize = audioRecordingTask.getSize() + sizeSoFar;
				fsize = bytesToMeg(fsize);

				size = Double.valueOf(df2.format(fsize));
			}
			else
			{
				size = 0;
			}

			timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds);
			sizeText = "Total recording size: " + size + " MB";

			recordingTime.setText(timeText);
			recordingSize.setText(sizeText);

			mHandler.postDelayed(this, 500);
		}
	};

	private void restoreSession(Bundle savedInstanceState)
	{
		Log.i(TAG, "restoreSession()");
		pauseCount = savedInstanceState.getInt("pauseCount");
		sizeSoFar = savedInstanceState.getLong("sizeSoFar");
		audioRecordingNotStarted = savedInstanceState.getBoolean("audioRecordingNotStarted");
		audioHasBeenRecorded = savedInstanceState.getBoolean("audioHasBeenRecorded");
		timeText = savedInstanceState.getString("timeText");
		sizeText = savedInstanceState.getString("sizeText");
	}

	private void drawCircles()
	{
		Bitmap bitMap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);  //creates bmp
		bitMap = bitMap.copy(bitMap.getConfig(), true);     //lets bmp to be mutable
		Canvas canvas = new Canvas(bitMap);                 //draw a canvas in defined bmp

		Paint paint = new Paint();                          //define paint and paint color
		paint.setColor(Color.RED);
		paint.setStyle(Style.FILL_AND_STROKE);
		paint.setStrokeWidth(5.0f);
		paint.setAntiAlias(true);                           //smooth edges

		ImageView imageView = (ImageView) findViewById(R.id.circle);
		imageView.setImageBitmap(bitMap);

		canvas.drawCircle(150, 150, 145, paint);
		//invalidate to update bitmap in imageview
		imageView.invalidate();
	}

	private void setUpButtons()
	{
		Log.i(TAG, "setUpButtons()");

		//drawCircles();
		final Button startButton = (Button) findViewById(R.id.AudioStartRecording);
		final Button AudioBtnFinishAndSave = (Button) findViewById(R.id.AudioBtnFinishAndSave);

		if(currentState == STATE_NOT_SET || currentState == STATE_STOPPED) // if (audioRecordingNotStarted)
		{
			startButton.setBackgroundResource(R.drawable.record_button);
			AudioBtnFinishAndSave.setEnabled(false);
			AudioBtnFinishAndSave.setAlpha(.5f);
		}

		startButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view)
			{
				Log.i(TAG, "startButton.setOnClickListener()");
				if(currentState == STATE_NOT_SET || currentState == STATE_STOPPED)
				{
					startRecording();

					startButton.setBackgroundResource(R.drawable.pause_button);
					currentState = STATE_RECORDING;
				}

				else if(currentState == STATE_RECORDING)
				{
					pauseRecording();

					startButton.setBackgroundResource(R.drawable.continue_button);
					AudioBtnFinishAndSave.setEnabled(true);
					AudioBtnFinishAndSave.setAlpha(1f);
					currentState = STATE_PAUSED;
				}

				else if(currentState == STATE_PAUSED)
				{
					startRecording();

					startButton.setBackgroundResource(R.drawable.pause_button);
					currentState = STATE_RECORDING;
				}

			}
		});

		// -------------------------------------------------
		// ----- SAVE AUDIO AND EXIT ACTIVITY --------------
		// -------------------------------------------------
		AudioBtnFinishAndSave.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view)
			{
				Log.i(TAG, "AudioBtnFinishAndSave.setOnClickListener()");
				savingLayout.setVisibility(View.VISIBLE);
				startButton.setVisibility(View.GONE);
				SaveAudio saveAudio = new SaveAudio(pauseCount);
				saveAudio.execute((Integer) null);

				File filenew = new File(finalStorageFilePath);
				int fileSize = Integer.parseInt(String.valueOf(filenew.length()));

				// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				// PASS BACK ANY INFORMATION BACK TO THE PLUGIN ENTRY POINT.
				Intent intent = new Intent();
				intent.putExtra("filePath", finalStorageFilePath);
				intent.putExtra("localURL", finalStorageFilePath);
				intent.putExtra("fileName", finalStorageFileName);
				intent.putExtra("fileExt", "wav");
				intent.putExtra("fileType", "audio/wav");
				intent.putExtra("fileSize", Integer.toString(fileSize));
				setResult(Activity.RESULT_OK, intent);
				// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				finish();
			}
		});
	}


	private long getMaxFileSize()
	{
		Log.i(TAG, "getMaxFileSize");
		return 30000000;
	}

	private void checkEvidenceFolderExists()
	{
		Log.i(TAG, "checkEvidenceFolderExists");

		File dir = new File(storageDirectory);

		if (!dir.exists())
		{
			Log.i(TAG, "Creating Nomad Folder");
			dir.mkdirs();
		}
		maxSize = getMaxFileSize();
	}

	private void checkExternalStorage()
	{
		Log.i(TAG, "checkExternalStorage");

		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state))
		{
			checkEvidenceFolderExists();
		}
		else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
		{
		}
		else
		{
		}
	}

	private void onAudioRecorderLowMemory()
	{
		Log.i(TAG, "onAudioRecorderLowMemory()");
		runOnUiThread(new Runnable() {
			public void run()
			{
				Log.i(TAG, "onAudioRecorderLowMemory() - runOnUiThread");
				final Button startButton = (Button) findViewById(R.id.AudioStartRecording);
				Button AudioBtnFinishAndSave = (Button) findViewById(R.id.AudioBtnFinishAndSave);
				AudioBtnFinishAndSave.setVisibility(View.VISIBLE);
				try
				{
				}
				catch(IllegalStateException e)
				{
					Toast.makeText(getApplicationContext(), fileSizeErrorMsg, Toast.LENGTH_SHORT).show();
				}
				startButton.setEnabled(false);
			}
		});
	}

	private void onAudioRecorderPaused()
	{
		Log.i(TAG, "onAudioRecorderPaused()");

		audioRecordingNotStarted = true;
		audioHasBeenRecorded = true;
		audioIsBeingRecorded = false;
		mHandler.removeCallbacks(mUpdateTimeTask);
		currentState_AudioTask = STATE_AUDIO_TASK_PAUSED;
	}

	private void onAudioRecorderError()
	{
		Log.i(TAG, "onAudioRecorderError()");

		audioRecordingNotStarted = true;
		audioHasBeenRecorded = false;
		audioIsBeingRecorded = false;
		errorOccured = true;

		mHandler.removeCallbacks(mUpdateTimeTask);
		currentState_AudioTask = STATE_AUDIO_TASK_ERROR;

		runOnUiThread(new Runnable() {
			public void run()
			{
				Button startButton = (Button) findViewById(R.id.AudioStartRecording);
				startButton.setBackgroundResource(R.drawable.pause_button);
				startButton.setEnabled(false);

				Toast.makeText(getApplicationContext(),"An error occured trying to record audio",
						Toast.LENGTH_LONG).show();
			}
		});
	}

	public void notifyAudioRecordingHasBegun()
	{
		Log.i(TAG, "notifyAudioRecordingHasBegun()");
		audioRecordingNotStarted = false;
		audioIsBeingRecorded = true;
		currentState_AudioTask = STATE_AUDIO_TASK_RECORDING;
	}

	public void updateTotalSize(long sizeOfThisRecording)
	{
		Log.i(TAG, "updateTotalSize()");
		sizeSoFar = sizeSoFar + sizeOfThisRecording;
	}

	private void createEvidenceFolder()
	{
		Log.i(TAG, "createEvidenceFolder()");

		String state = android.os.Environment.getExternalStorageState();

		if (!state.equals(android.os.Environment.MEDIA_MOUNTED))
		{
		}

		File dir = new File(storageDirectory);
		if (!dir.exists())
		{
			Log.i(TAG, "Creating Nomad Folder");
			dir.mkdirs();
		}
	}

	private void startRecording()
	{
		Log.i(TAG, "startRecording()");
		if (mStartTime == 0L)
			mStartTime = System.currentTimeMillis();
		mStartTime = System.currentTimeMillis() - mTotalTime;
		mHandler.removeCallbacks(mUpdateTimeTask);
		mHandler.postDelayed(mUpdateTimeTask, 100);
		savingLayout.setVisibility(View.GONE);

		pauseCount++;

		createEvidenceFolder();
		getFilePath();

		Log.i(TAG, "Recording to file: " + audioOutputPath);

		audioRecordingTask = new AudioRecordingTask();
		audioRecordingTask.setUp(this, audioOutputPath, maxSize, sizeSoFar);
		audioRecordingTask.execute();
	}

	private void pauseRecording()
	{
		Log.i(TAG, "pauseRecording()");

		mHandler.removeCallbacks(mUpdateTimeTask);

		if (audioRecordingTask != null)
			audioRecordingTask.pause();
	}

	private void getFilePath()
	{
		Log.i(TAG, "getFilePath()");

		File file = new File(storageDirectory + "temp_audio_" + pauseCount + ".pcm");

		if (!file.exists())
		{
			try
			{
				Log.i(TAG, "getFilePath() - file.createNewFile");
				file.createNewFile();
			}
			catch (IOException e2)
			{
				e2.printStackTrace();
			}
		}
		audioOutputPath = file.getAbsolutePath();
	}

	// ------------------------------------------------------------------
	// ----- AudioRecordingTask - AsyncTask - Record Audio --------------
	// ------------------------------------------------------------------
	static class AudioRecordingTask extends AsyncTask<Void, Void, Void> {
		private String TAG = "AudioRecorder - AudioRecordingTask - AsyncTask";
		AudioRecorder activity = null;
		int progress = 0;
		private String outputPath;
		private boolean stopped = false;
		private boolean error = false;
		private boolean paused = false;
		private long maxSize;
		private long sizeSoFar;
		private long sizeOfThisRecording;

		DataOutputStream dataOutStream;

		AudioRecordingTask()
		{
		}

		public void setUp(AudioRecorder activity, String outputPath, long maxSize, long sizeSoFar)
		{
			attach(activity);

			this.outputPath = outputPath;
			this.maxSize = maxSize;
			this.sizeSoFar = sizeSoFar;
			activity.currentState_AudioTask = STATE_AUDIO_TASK_NOT_SET;
			Log.i(TAG, "AudioRecordingTask - maxSize " + maxSize);
			Log.i(TAG, "AudioRecordingTask - sizeSoFar " + sizeSoFar);
		}

		@Override
		protected Void doInBackground(Void... unused)
		{

			activity.notifyAudioRecordingHasBegun();

			int frequency = RECORDING_FREQUENCY;
			int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
			int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

			final int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);

			// Create hardware audio recorder
			AudioRecord audioRecord = null;
			try
			{
				audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration,
						audioEncoding, bufferSize);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}

			File file = new File(outputPath);

			if (file.exists())
				file.delete();
			try {
				file = new File(outputPath);
			}
			catch (NullPointerException e)
			{
				e.printStackTrace();
			}

			OutputStream oStream = null;
			try
			{
				oStream = new FileOutputStream(file);
			}
			catch (FileNotFoundException e2)
			{
				e2.printStackTrace();
			}

			dataOutStream = new DataOutputStream(oStream);

			byte[] buffer = new byte[bufferSize];

			if(audioRecord != null)
			{
				audioRecord.startRecording();
				activity.currentState_AudioTask = STATE_AUDIO_TASK_RECORDING;
			}
			else
			{
				stopped = true;
				error = true;
				activity.currentState_AudioTask = STATE_AUDIO_TASK_ERROR;
				Log.i(activity.TAG, "audio recording not started - STATE_AUDIO_TASK_ERROR");
			}

			while (!stopped)
			{
				int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);

				try
				{
					dataOutStream.write(buffer, 0, bufferReadResult);
				}
				catch (IOException e1)
				{
					e1.printStackTrace();
				}

				if ((dataOutStream.size() + sizeSoFar) > maxSize)
				{
					stopped = true;
					activity.currentState_AudioTask = STATE_AUDIO_TASK_STOPPED;
					Log.i(activity.TAG, "dataOutStream too big - STATE_AUDIO_TASK_STOPPED");
				}
			}

			sizeOfThisRecording = dataOutStream.size();

			try
			{
				dataOutStream.flush();
				dataOutStream.close();
			}
			catch (IOException e1)
			{
				e1.printStackTrace();
			}

			if(audioRecord != null)
			{
				audioRecord.stop();
				audioRecord.release();
				audioRecord = null;
				activity.currentState_AudioTask = STATE_AUDIO_TASK_STOPPED;
			}

			if (activity != null)
			{
				activity.updateTotalSize(sizeOfThisRecording);
				if (stopped && !paused && !error)
				{
					if (activity != null)
					{
						activity.onAudioRecorderPaused();
						//activity.onAudioRecorderLowMemory();
					}
				}
				else if (paused)
					activity.onAudioRecorderPaused();
				else if (error)
					activity.onAudioRecorderError();
			}
			return (null);
		}

		public int getSize()
		{
			if (dataOutStream != null)
				return dataOutStream.size();
			else
				return 0;
		}

		public void pause()
		{
			stopped = true;
			paused = true;
			activity.currentState_AudioTask = STATE_AUDIO_TASK_PAUSED;
		}

		void detach()
		{
			activity = null;
		}

		void attach(AudioRecorder activity)
		{
			this.activity = activity;
		}

	} // static class AudioRecordingTask extends AsyncTask<Void, Void, Void> {}

	// ------------------------------------------------------------------
	// ----- SaveAudio - AsyncTask --------------------------------------
	// ------------------------------------------------------------------
	private class SaveAudio extends AsyncTask<Object, Integer, Void> {
		private int _pauseCount;
		private String TAG = "AudioRecorder - SaveAudio - AsyncTask";

		public SaveAudio(int pauseCount)
		{
			this._pauseCount = pauseCount;
		}

		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(Object... arg0)
		{
			if (_pauseCount > 0)
			{
				for (int x = 0; x <= _pauseCount - 1; x++)
				{
					File f1 = new File(tempStorageFilePath);
					File f2 = new File(tempPart1StorageFilePath  + (x + 1) + ".pcm");
					mergePCM(f1, f2);
					Log.i(TAG, tempPart1StorageFilePath  + (x + 1) + ".pcm");
				}
			}
			File f1 = new File(tempStorageFilePath);
			Log.i(TAG, tempStorageFilePath);
			// Convert the PCM data into a readable WAVE format:
			convertPCMtoWAV(f1);
			return null;
		}

		@Override
		protected void onCancelled()
		{
			super.onCancelled();
		}

		private void convertPCMtoWAV(File fileToConvert)
		{
			Log.i(TAG, "Starting conversion");
			try
			{
				long mySubChunk1Size = 16;
				int myBitsPerSample = 16;
				int myFormat = 1;
				long myChannels = 1;
				long mySampleRate = RECORDING_FREQUENCY;
				long myByteRate = mySampleRate * myChannels * myBitsPerSample / 8;
				int myBlockAlign = (int) (myChannels * myBitsPerSample / 8);

				// 8 to 16 bit
				long myDataSize = fileToConvert.length();
				long myChunk2Size = myDataSize * myChannels * myBitsPerSample / 8;
				long myChunkSize = 36 + myChunk2Size;

				OutputStream os;
				os = new FileOutputStream(new File(finalStorageFilePath));
				Log.i(TAG,finalStorageFilePath);

				BufferedOutputStream bos = new BufferedOutputStream(os);
				DataOutputStream outFile = new DataOutputStream(bos);

				outFile.writeBytes("RIFF");
				outFile.write(intToByteArray((int) myChunkSize), 0, 4);
				outFile.writeBytes("WAVE");
				outFile.writeBytes("fmt ");
				outFile.write(intToByteArray((int) mySubChunk1Size), 0, 4);
				outFile.write(shortToByteArray((short) myFormat), 0, 2);
				outFile.write(shortToByteArray((short) myChannels), 0, 2);
				outFile.write(intToByteArray((int) mySampleRate), 0, 4);
				outFile.write(intToByteArray((int) myByteRate), 0, 4);
				outFile.write(shortToByteArray((short) myBlockAlign), 0, 2);
				outFile.write(shortToByteArray((short) myBitsPerSample), 0, 2);
				outFile.writeBytes("data");
				outFile.write(intToByteArray((int) myDataSize), 0, 4);

				InputStream in = new FileInputStream(fileToConvert);

				byte[] buf = new byte[1024];

				int len;

				while ((len = in.read(buf)) > 0)
				{
					outFile.write(buf, 0, len);
				}

				in.close();

				outFile.flush();
				outFile.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		private void mergePCM(File f1, File f2)
		{
			try
			{
				InputStream in;

				in = new FileInputStream(f2);

				OutputStream out = new FileOutputStream(f1, true);

				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0)
				{
					out.write(buf, 0, len);
				}

				in.close();
				out.close();

				f2.delete();
			}
			catch (FileNotFoundException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		private byte[] intToByteArray(int i)
		{
			byte[] b = new byte[4];
			b[0] = (byte) (i & 0x00FF);
			b[1] = (byte) ((i >> 8) & 0x000000FF);
			b[2] = (byte) ((i >> 16) & 0x000000FF);
			b[3] = (byte) ((i >> 24) & 0x000000FF);
			return b;
		}

		public byte[] shortToByteArray(short data)
		{
			return new byte[] { (byte) (data & 0xff), (byte) ((data >>> 8) & 0xff) };
		}

		@Override
		protected void onPostExecute(Void result)
		{
			Log.i(TAG, "Finished conversion");
			super.onPostExecute(result);
		}
	} // private class SaveAudio extends AsyncTask<Object, Integer, Void> {}
}
