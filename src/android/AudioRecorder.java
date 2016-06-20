package uk.co.onefile.nomadionic.audiocapture;

import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

public class AudioRecorder extends Activity {

	private String TAG = "AudioRecorder";

	public static final Integer RECORDING_FREQUENCY = 11025;

	private Integer maxUpload = 0;
	private String audioOutputPath = "";
	private Integer pauseCount = -1;
	private String fileSizeErrorMsg = "";

	private long sizeSoFar = 50000;
	private long maxSize = 30000000;

	private LinearLayout savingLayout;
//    private AppStorage localStorage;

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

	private String timeText = "Total recording time: 0:00";
	private String sizeText = "Total recording size: 0.00 MB";

	private Handler mHandler = new Handler();

	private static final DecimalFormat df2 = new DecimalFormat("#,###,###,##0.00");

	private long uploadLimit = 0;
	private String storageDirectory;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		super.onCreate(savedInstanceState);
		setTitleColor(Color.parseColor("#FFFFFF"));
		setTitle("Audio Recorder");
		Log.i(TAG, "AudioRecorder - OnCreate");
//        localStorage = ((AppStorage) getApplicationContext());
		if (savedInstanceState != null)
		{
			restoreSession(savedInstanceState);
		}

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
			long millis = System.currentTimeMillis() - mStartTime;
			int seconds = (int) (millis / 1000);
			int minutes = seconds / 60;
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

			timeText = "Total recording time: " + String.format("%d:%02d", minutes, seconds);
			sizeText = "Total recording size: " + size + " MB";

			recordingTime.setText(timeText);
			recordingSize.setText(sizeText);

			mHandler.postDelayed(this, 500);
		}
	};

	private void restoreSession(Bundle savedInstanceState)
	{
		pauseCount = savedInstanceState.getInt("pauseCount");
		sizeSoFar = savedInstanceState.getLong("sizeSoFar");
		audioRecordingNotStarted = savedInstanceState.getBoolean("audioRecordingNotStarted");
		audioHasBeenRecorded = savedInstanceState.getBoolean("audioHasBeenRecorded");
		timeText = savedInstanceState.getString("timeText");
		sizeText = savedInstanceState.getString("sizeText");
	}

	private void setMaxUpload()
	{
	}

	private void setFileSizeMessage()
	{
	}

	private void setUpButtons()
	{
		final Button startButton = (Button) findViewById(R.id.AudioStartRecording);
		final Button AudioBtnFinishAndSave = (Button) findViewById(R.id.AudioBtnFinishAndSave);
		final Button confirmButton = (Button) findViewById(R.id.AudioCancelRecording);
		final ProgressBar RecordingProgressBar = (ProgressBar) findViewById(R.id.recordingBar);
		AudioBtnFinishAndSave.setEnabled(false);
		AudioBtnFinishAndSave.setAlpha(.5f);

		Log.i(TAG, "AudioRecorder - setUpButtons");

		if (audioHasBeenRecorded)
		{
			startButton.setText("Resume Recording");
			AudioBtnFinishAndSave.setEnabled(true);
			AudioBtnFinishAndSave.setAlpha(1f);
			RecordingProgressBar.setVisibility(View.GONE);
		}
		else if (audioIsBeingRecorded)
		{
			startButton.setText("Pause Recording");
			RecordingProgressBar.setVisibility(View.VISIBLE);
		}

		startButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view)
			{
				if (audioRecordingNotStarted)
				{
					startRecording();

					startButton.setText("Pause Recording");

					RecordingProgressBar.setVisibility(View.VISIBLE);

					if (mStartTime == 0L)
					{
						mStartTime = System.currentTimeMillis();

					}

					mStartTime = System.currentTimeMillis() - mTotalTime;

					mHandler.removeCallbacks(mUpdateTimeTask);
					mHandler.postDelayed(mUpdateTimeTask, 100);
				}
				else
				{
					if (audioRecordingTask != null)
					{
						audioRecordingTask.pause();
						Log.i(TAG, "Pausing recording");
					}

					startButton.setText("Resume Recording");

					RecordingProgressBar.setVisibility(View.GONE);

					mHandler.removeCallbacks(mUpdateTimeTask);
				}

			}
		});

		AudioBtnFinishAndSave.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view)
			{
				savingLayout.setVisibility(View.VISIBLE);
				startButton.setVisibility(View.GONE);
				confirmButton.setVisibility(View.GONE);

				SaveAudio saveAudio = new SaveAudio(pauseCount);
				saveAudio.execute((Integer) null);

				setResult(RESULT_OK);
				finish();
			}
		});

		confirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view)
			{
				cancelRecording();
			}
		});
	}

	private void onAudioRecorderLowMemory()
	{
		runOnUiThread(new Runnable() {
			public void run()
			{
				final ProgressBar RecordingProgressBar = (ProgressBar) findViewById(R.id.recordingBar);
				final Button startButton = (Button) findViewById(R.id.AudioStartRecording);
				final Button confirmButton = (Button) findViewById(R.id.AudioCancelRecording);
				Button AudioBtnFinishAndSave = (Button) findViewById(R.id.AudioBtnFinishAndSave);
				AudioBtnFinishAndSave.setVisibility(View.VISIBLE);
				try
				{
					//displayAlertBox(fileSizeErrorMsg, null, null, "OK", false, null);
				}
				catch(IllegalStateException e)
				{
					Toast.makeText(getApplicationContext(), fileSizeErrorMsg, Toast.LENGTH_SHORT).show();
				}

				RecordingProgressBar.setVisibility(View.GONE);

				startButton.setEnabled(false);

				startButton.setVisibility(View.GONE);
				confirmButton.setVisibility(View.GONE);
			}
		});
	}

	@Override
	protected void onPause()
	{
		Log.i(TAG, "onPause()");

		if (audioRecordingTask != null)
		{
			audioRecordingTask.pause();
			Log.i(TAG, "Pausing recording");
		}

		while (!audioRecordingNotStarted)
		{
			// wait
		}

		super.onPause();
	}

	@Override
	protected void onResume()
	{
		Log.i(TAG, "onResume()");
		super.onResume();

		this.setMaxUpload();

		setContentView(R.layout.evidence_activity_new_audio_recorder);

		recordingTime = (TextView) findViewById(R.id.recordingTime);
		recordingSize = (TextView) findViewById(R.id.recordingSize);
		maxRecordingSize = (TextView) findViewById(R.id.maxSize);

		savingLayout = (LinearLayout) findViewById(R.id.savingLayout);

		recordingTime.setText(timeText);
		recordingSize.setText(sizeText);
		maxRecordingSize.setText("Max recording size: "+uploadLimit+" MB");

		checkExternalStorage();
	}

	private long getMaxFileSize()
	{
		return 30000;
	}
	private void checkEvidenceFolderExists()
	{
		File dir = new File(storageDirectory);

		if (!dir.exists())
		{
			Log.i(TAG, "Creating Nomad Folder");
			dir.mkdirs();
		}

		setFileSizeMessage();
		maxSize = getMaxFileSize();

		setUpButtons();
	}

	private void checkExternalStorage()
	{
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state))
		{
			// We can read and write the media

			checkEvidenceFolderExists();
		}
		else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
		{
			// We can only read the media

			//displayAlertBox("Cannot write to external media - plase ensure it is currently connected", "Retry", "Cancel", null, false, -1);
		}
		else
		{
			// Something else is wrong. It may be one of many other states, but
			// all we need to know is we can neither read nor write

			//displayAlertBox("Cannot write to external media - plase ensure it is currently connected", "Retry", "Cancel", null, false, -1);

		}
	}

	private void onAudioRecorderPaused()
	{
		Log.i(TAG, "onAudioRecorderPaused()");

		audioRecordingNotStarted = true;
		audioHasBeenRecorded = true;
		audioIsBeingRecorded = false;

		mHandler.removeCallbacks(mUpdateTimeTask);

		runOnUiThread(new Runnable() {
			public void run()
			{
				final ProgressBar RecordingProgressBar = (ProgressBar) findViewById(R.id.recordingBar);
				Button AudioBtnFinishAndSave = (Button) findViewById(R.id.AudioBtnFinishAndSave);
				AudioBtnFinishAndSave.setEnabled(true);
				AudioBtnFinishAndSave.setAlpha(1f);
				RecordingProgressBar.setVisibility(View.GONE);
			}
		});
	}

	private void cancelRecording()
	{
		if(!errorOccured)
		{
			//displayAlertBox("Are you sure you want to cancel this recording?", "Yes", "No", null, false, 1);
		}
	}

	public void onPositiveSelection(Integer taskID)
	{
		if(taskID == 1)
		{
			setResult(RESULT_CANCELED);
			finish();
		}
		else if (taskID == -1)
		{
			checkExternalStorage();
		}
	}

	public void onNegativeSelection(Integer taskID)
	{
		if (taskID!=1)
		{
			finish();
		}
	}

	private void onAudioRecorderError()
	{
		Log.i(TAG, "onAudioRecorderError()");

		audioRecordingNotStarted = true;
		audioHasBeenRecorded = false;
		audioIsBeingRecorded = false;
		errorOccured = true;

		mHandler.removeCallbacks(mUpdateTimeTask);

		runOnUiThread(new Runnable() {
			public void run()
			{
				final ProgressBar RecordingProgressBar = (ProgressBar) findViewById(R.id.recordingBar);
				Button AudioBtnCancel = (Button) findViewById(R.id.AudioCancelRecording);
				AudioBtnCancel.setVisibility(View.VISIBLE);

				AudioBtnCancel.setOnClickListener(new View.OnClickListener() {

					public void onClick(View v)
					{
						audioRecordingTask = null;
						cancelRecording();
					}
				});

				Button startButton = (Button) findViewById(R.id.AudioStartRecording);
				startButton.setVisibility(View.GONE);

				Toast.makeText(getApplicationContext(),"An error occured trying to record audio",
						Toast.LENGTH_LONG).show();

				RecordingProgressBar.setVisibility(View.GONE);
			}
		});
	}

	public void notifyAudioRecordingHasBegun()
	{
		Log.i(TAG, "notifyAudioRecordingHasBegun()");
		audioRecordingNotStarted = false;
		audioIsBeingRecorded = true;
	}

	public void updateTotalSize(long sizeOfThisRecording)
	{
		Log.i(TAG, "updateTotalSize()");
		sizeSoFar = sizeSoFar + sizeOfThisRecording;
	}

	private void createEvidenceFolder()
	{
		String state = android.os.Environment.getExternalStorageState();

		if (!state.equals(android.os.Environment.MEDIA_MOUNTED))
		{
			//displayAlertBox("SD Card is not mounted.  It is " + state + ".", null, null, "OK", false, null);
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
		Button AudioBtnFinishAndSave = (Button) findViewById(R.id.AudioBtnFinishAndSave);
		if (audioRecordingNotStarted)
		{
			AudioBtnFinishAndSave.setEnabled(false);
			AudioBtnFinishAndSave.setAlpha(.5f);
		}
		else
		{
			AudioBtnFinishAndSave.setEnabled(true);
			AudioBtnFinishAndSave.setAlpha(0f);
		}

		savingLayout.setVisibility(View.GONE);

		pauseCount++;

		createEvidenceFolder();
		getFilePath();

		Log.i(TAG, "Recording to file: " + audioOutputPath);

		audioRecordingTask = new AudioRecordingTask();
		audioRecordingTask.setUp(this, audioOutputPath, maxSize, sizeSoFar);
		audioRecordingTask.execute();
	}

	private void getFilePath()
	{
		File file = new File(storageDirectory + "temp_audio_" + pauseCount + ".pcm");

		if (!file.exists())
		{
			try
			{
				file.createNewFile();
			}
			catch (IOException e2)
			{
				e2.printStackTrace();
			}
		}

		audioOutputPath = file.getAbsolutePath();
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
		//displayAlertBox("Are you sure you want to cancel this recording?", "Yes", "No", null, false, 1);
	}

	private class SaveAudio extends AsyncTask<Object, Integer, Void> {

		private int _pauseCount;

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
					File f1 = new File(storageDirectory + "temp_audio_0.pcm");
					File f2 = new File(storageDirectory + "temp_audio_" + (x + 1) + ".pcm");
					mergePCM(f1, f2);
				}
			}

			File f1 = new File(storageDirectory + "temp_audio_0.pcm");

			// Convert the PCM data into a readable WAVE format:
			properWAV(f1);

			return null;
		}

		@Override
		protected void onCancelled()
		{
			super.onCancelled();

		}

		private void properWAV(File fileToConvert)
		{
			Log.i(TAG, "********************************");
			Log.i(TAG, "Starting conversion");
			Log.i(TAG, "********************************");

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

				long myDataSize = fileToConvert.length();// clipData.length;
				long myChunk2Size = myDataSize * myChannels * myBitsPerSample / 8;
				long myChunkSize = 36 + myChunk2Size;

				OutputStream os;
				os = new FileOutputStream(new File(storageDirectory + "OneFileAudio.wav"));
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
			Log.i(TAG, "********************************");
			Log.i(TAG, "Finished conversion");
			Log.i(TAG, "********************************");
			super.onPostExecute(result);

		}

	}

	static class AudioRecordingTask extends AsyncTask<Void, Void, Void> {
		AudioRecorder activity = null;
		int progress = 0;

		private String outputPath;

		private boolean stopped = false;
		private boolean error = false;
		private boolean paused = false;

		private long maxSize;
		private long sizeSoFar;

		private long sizeOfThisRecording;

		DataOutputStream dos;

		AudioRecordingTask()
		{

		}

		public void setUp(MainActivity activity, String outputPath, long maxSize, long sizeSoFar)
		{
			attach(activity);

			this.outputPath = outputPath;
			this.maxSize = maxSize;
			this.sizeSoFar = sizeSoFar;
		}

		@Override
		protected Void doInBackground(Void... unused)
		{

			activity.notifyAudioRecordingHasBegun();

			int frequency = RECORDING_FREQUENCY;
			int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
			int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

			final int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);

			AudioRecord audioRecord = null;

			try
			{
				audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);
			}
			catch(Exception e)
			{

			}

			File file = new File(outputPath);

			if (file.exists())
				file.delete();

			file = new File(outputPath);

			OutputStream os = null;
			try
			{
				os = new FileOutputStream(file);
			}
			catch (FileNotFoundException e2)
			{
				e2.printStackTrace();
			}

			dos = new DataOutputStream(os);

			byte[] buffer = new byte[bufferSize];

			if(audioRecord != null)
			{
				audioRecord.startRecording();
			}
			else
			{
				stopped = true;
				error = true;
			}

			while (!stopped)
			{
				int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);

				try
				{
					dos.write(buffer, 0, bufferReadResult);
				}
				catch (IOException e1)
				{
					e1.printStackTrace();
				}

				if ((dos.size() + sizeSoFar) > maxSize)
				{
					stopped = true;
				}

			}

			sizeOfThisRecording = dos.size();

			try
			{
				dos.flush();
				dos.close();
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
			}


			if (activity != null)
			{
				activity.updateTotalSize(sizeOfThisRecording);


				if (stopped && !paused && !error)
				{
					if (activity != null)
					{
						activity.onAudioRecorderPaused();
						activity.onAudioRecorderLowMemory();
					}
				}
				else if (paused)
				{
					activity.onAudioRecorderPaused();
				}
				else if (error)
				{
					activity.onAudioRecorderError();
				}
			}

			return (null);
		}

		public int getSize()
		{
			if (dos != null)
			{
				return dos.size();
			}
			else
			{
				return 0;
			}
		}

		public void pause()
		{
			stopped = true;
			paused = true;
		}

		void detach()
		{
			activity = null;
		}

		void attach(AudioRecorder activity)
		{
			this.activity = activity;
		}

	}
}
