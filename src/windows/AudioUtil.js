(function () {
	'use strict';
	var captureInitSettings;
	var oMediaCapture;
	var profile;
	var storageFile;
	var deviceList = new Array();
	var isRecording = false;
	var isPaused = false;
	var audioStream;
	var pausedBuffer = null;
	var dataReader = null;
	var fileIO = Windows.Storage.FileIO;

	WinJS.Namespace.define('AudioUtil', {
		init: init,
		startRecord: startMediaCaptureSession,
		stopRecord: stopMediaCaptureSession,
		pauseRecord: pauseMediaCaptureSession,
		cleanupCaptureResources: cleanupCaptureResources
	});

	function init() {
		enumerateCameras();
	}

	// Identify available cameras.
	function enumerateCameras() {
		var deviceInfo = Windows.Devices.Enumeration.DeviceInformation;
		return deviceInfo.findAllAsync(Windows.Devices.Enumeration.DeviceClass.videoCapture)
		.then(function (devices) {
			// Add the devices to deviceList
			if (devices.length > 0) {
				for (var i = 0; i < devices.length; i++) {
					deviceList.push(devices[i]);
				}
				initCaptureSettings();
				initMediaCapture();
			} else {
				return WinJS.Promise.wrapError('No Camera');
			}
		});
	}

	// Initialize the MediaCaptureInitialzationSettings.
	function initCaptureSettings() {
		captureInitSettings = null;
		captureInitSettings = new Windows.Media.Capture.MediaCaptureInitializationSettings();
		captureInitSettings.audioDeviceId = "";
		captureInitSettings.videoDeviceId = "";
		captureInitSettings.streamingCaptureMode = Windows.Media.Capture.StreamingCaptureMode.audio;
		captureInitSettings.photoCaptureSource = Windows.Media.Capture.PhotoCaptureSource.videoPreview;
		if (deviceList.length > 0) {
			captureInitSettings.videoDeviceId = deviceList[0].id;
		}
	}

	// Create and initialize the MediaCapture object.
	function initMediaCapture() {
		oMediaCapture = null;
		oMediaCapture = new Windows.Media.Capture.MediaCapture();
		return oMediaCapture.initializeAsync(captureInitSettings)
		.then(function (result) {
			createProfile();
		});
	}

	// Create a profile.
	function createProfile() {
		profile = Windows.Media.MediaProperties.MediaEncodingProfile.createMp3(
			Windows.Media.MediaProperties.AudioEncodingQuality.medium);
	}

	// Start the audio capture.
	function startMediaCaptureSession() {
		audioStream = new Windows.Storage.Streams.InMemoryRandomAccessStream();
		return oMediaCapture.startRecordToStreamAsync(profile, audioStream)
		.then(function () {
			isRecording = true;
			isPaused = false;
		});
	}

	// Pause the audio capture
	function pauseMediaCaptureSession() {
		return oMediaCapture.stopRecordAsync()
			.then(function () {
				isRecording = false;
				dataReader = new Windows.Storage.Streams.DataReader(audioStream.getInputStreamAt(0));
				return dataReader.loadAsync(audioStream.size);
			})
			.then(function () {
				if (pausedBuffer === null) {
					pausedBuffer = new Uint8Array(audioStream.size);
					dataReader.readBytes(pausedBuffer);
				} else {
					var tmp = new Uint8Array(audioStream.size);
					dataReader.readBytes(tmp);
					pausedBuffer = pushUint8Array(pausedBuffer, tmp);
				}
				dataReader.close();
				isPaused = true;
			});
	}

	// Stop the audio capture.
	function stopMediaCaptureSession() {
		var buffer;
		var sFile;
		var promise;

		if (!isPaused) {
			promise = oMediaCapture.stopRecordAsync();
		} else {
			promise = WinJS.Promise.as(null);
		}
		return promise
			.then(function () {
				isRecording = false;
				return Windows.Storage.ApplicationData.current.temporaryFolder.createFileAsync("recording.mp3", Windows.Storage.CreationCollisionOption.replaceExisting)
			})
			.then(function (newFile) {
				sFile = newFile;
				dataReader = new Windows.Storage.Streams.DataReader(audioStream.getInputStreamAt(0));
				return dataReader.loadAsync(audioStream.size);
			})
			.then(function () {
				buffer = new Uint8Array(audioStream.size);
				dataReader.readBytes(buffer);
				if (pausedBuffer !== null && !isPaused) {
					pausedBuffer = pushUint8Array(pausedBuffer, buffer);
					return fileIO.writeBytesAsync(sFile, pausedBuffer);
				} else if (isPaused) {
					return fileIO.writeBytesAsync(sFile, pausedBuffer);
				} else {
					return fileIO.writeBytesAsync(sFile, buffer);
				}
			})
			.then(function () {
				return sFile;
			});
	}

	function pushUint8Array(source, addition) {
		var newArr = new Uint8Array(source.length + addition.length);
		newArr.set(source, 0);
		newArr.set(addition, source.length);
		return newArr;
	}

	function cleanupCaptureResources() {
		var promises = [];
		if (oMediaCapture) {
			if (isRecording) {
				promises.push(oMediaCapture.stopRecordAsync().then(function () {
					isRecording = false;
				}));
			}

			promises.push(new WinJS.Promise(function (complete) {
				oMediaCapture.close();
				oMediaCapture = null;
				complete();
			}));
		}
		return WinJS.Promise.join(promises).done(null, errorHandler);
	}

	function errorHandler(error) {
		oMediaCapture = null;
	}
})();