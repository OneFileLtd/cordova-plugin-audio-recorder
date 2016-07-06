(function () {
	"use strict";
	var CDVAudioRecorderProxy = {
		audioRecorder: function (win, fail, args, env) {
			try {
				successCB = win;
				errorCB = fail;
				callerOptions = args;
				WinJS.Navigation.navigate('/pages/AudioRecorder.html');
			} catch (e) {
				fail(e);
			}
		}
	};

	WinJS.Namespace.define('Timer', {
		currentTime: 0,
		startTime: 0,
		pausedTime: 0,
		elapsedTime: 0,
		formattedTime: '0.000s'
	});

	WinJS.Navigation.addEventListener("navigating", function (e) {
		var elem = document.createElement("div");
		elem.id = 'audioRecorderPluginPage';
		var body = document.getElementsByTagName("body")[0];
		body.appendChild(elem);
		WinJS.Utilities.addClass(elem, 'plugin-page');
		WinJS.UI.Animation.exitPage(elem.children).then(function () {
			WinJS.Utilities.empty(elem);
			WinJS.UI.Pages.render(e.detail.location, elem)
                .then(function () {
                	return WinJS.UI.Animation.enterPage(elem.children)
                });
		});
	});

	var state = 'unstarted';
	var timerBinding = WinJS.Binding.as(Timer);
	var successCB;
	var errorCB;
	var callerOptions;

	WinJS.UI.Pages.define("/pages/AudioRecorder.html", {
		ready: pageReady,
		unload: function () {
			// TODO: Respond to navigations away from this page.
		},
		updateLayout: function (element) {
		},
		_saveClick: saveClick,
		_recordClick: recordClick,
		_resize: resize,
		_backClick: backClick
	});

	function pageReady(element, options) {
		WinJS.Application.addEventListener("checkpoint", checkpointHandler);
		resetTimer();
		WinJS.Binding.processAll(element, timerBinding);
		window.onresize = this._resize.bind(this);
		saveButton.onclick = this._saveClick.bind(this);
		recordButton.onclick = this._recordClick.bind(this);
		backButton.onclick = this._backClick.bind(this);
		AudioUtil.init();
		resize();
	}

	function checkpointHandler(args) {
		args.setPromise(AudioUtil.cleanupCaptureResources());
	}

	function backClick() {
		navBack(true, { status: 1 });
	}

	function resetTimer() {
		timerBinding.currentTime = 0;
		timerBinding.startTime = 0;
		timerBinding.pausedTime = 0;
		timerBinding.elapsedTime = 0;
		timerBinding.formattedTime = '0.000s';
	}

	function navBack(isSuccess, data) {
		WinJS.Navigation.back(1);
		WinJS.Navigation.history = {};
		var elem = document.getElementById('audioRecorderPluginPage');
		var body = document.getElementsByTagName("body")[0];
		WinJS.UI.Animation.exitPage(elem.children).then(function () {
			body.removeChild(elem);
			if (isSuccess) {
				successCB(data);
				return;
			}
			errorCB(data);
			return;
		});
	}

	function runTimer() {
		if (state === 'recording') {
			timerBinding.currentTime = Date.now();
			timerBinding.elapsedTime = timerBinding.pausedTime + (timerBinding.currentTime - timerBinding.startTime);
			timerBinding.formattedTime = (timerBinding.elapsedTime / 1000).toFixed(3).toString() + 's';
		}
		return WinJS.Promise.timeout(10)
			.then(runTimer);
	}

	function recordClick(evt) {
		if (state === 'unstarted') {
			runTimer();
		}
		if (state === 'unstarted' || state === 'paused') {
			timerBinding.startTime = Date.now();
		}

		if (state === 'recording') {
			AudioUtil.pauseRecord();
			state = 'paused';
		} else {
			AudioUtil.startRecord();
			state = 'recording';
		}
		if (state === 'paused') {
			timerBinding.pausedTime = timerBinding.elapsedTime;
			timerBinding.startTime = 0;
			timerBinding.currentTime = 0;
		}
		saveButton.className = 'available';
		audioPlayer.className = state;
		arcpath(10);
	}

	function saveClick(evt) {
		var capturedFile;
		AudioUtil.stopRecord()
		.then(function (file) {
			capturedFile = file;
			return file.getBasicPropertiesAsync();
		})
		.then(function (basicProperties) {
			navBack(true, {
				status: 2,
				fileDetails: {
					fullPath: nativePathToCordova(capturedFile.path),
					localURL: nativePathToCordova(capturedFile.path),
					name: capturedFile.name,
					size: basicProperties.size,
					ext: capturedFile.fileType.replace('.', ''),
					type: capturedFile.contentType
				}
			});
		}).done(null, function error(err) {
			navBack(false, err);
		});
	}

	function cordovaPathToNative(path) {
		// turn / into \\
		var cleanPath = path.replace(/\//g, '\\');
		// turn  \\ into \
		cleanPath = cleanPath.replace(/\\+/g, '\\');
		return cleanPath;
	}

	function nativePathToCordova(path) {
		var cleanPath = path.replace(/\\/g, '/');
		return cleanPath;
	}

	function resize(evt) {
		if (window.innerHeight > window.innerWidth) {
			WinJS.Utilities.query('body').removeClass('aspect-landscape');
		} else {
			WinJS.Utilities.query('body').addClass('aspect-landscape');
		}
	}

	function arcpath(percent) {
		var point = getPointOnCircle(convertToRadians(percent * 360 / 100));
		var path = 'M0,0l0,-1a1,1,1,';
		path += percent > 50 ? '1' : '0';
		path += ',1,' + (point.x) + ',' + (1 + point.y) + 'Z';
		pieslice.setAttribute("d", path);
	}

	function convertToRadians(angle) {
		return angle * (Math.PI / 180) - Math.PI / 2;
	}

	function getPointOnCircle(angle) {
		return {
			x: Math.cos(angle),
			y: Math.sin(angle)
		};
	}

	require("cordova/exec/proxy").add("CDVAudioRecorder", CDVAudioRecorderProxy);
})();