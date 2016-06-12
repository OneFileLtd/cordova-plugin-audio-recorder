var exec = require('cordova/exec');
var AudioRecorder = function () {};

AudioRecorder.prototype.audioRecorder = function(options, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'CDVAudioRecorder', 'audioRecorder', [options]);
};

module.exports = new AudioRecorder();
