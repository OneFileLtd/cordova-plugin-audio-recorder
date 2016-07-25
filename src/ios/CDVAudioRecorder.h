#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
#import <QuartzCore/QuartzCore.h>
#ifndef DEV_PLUGING
#import <Cordova/CDVPlugin.h>
#import "CDVFile.h"
#endif

enum CDVAUDIOError {
    AUDIO_INTERNAL_ERR = 0,
    AUDIO_APPLICATION_BUSY = 1,
    AUDIO_INVALID_ARGUMENT = 2,
    AUDIO_NO_MEDIA_FILES = 3,
    AUDIO_NOT_SUPPORTED = 20
};
typedef NSUInteger CDVAUDIOError;

typedef enum {
    STATE_NOT_SET = 0,
    STATE_RECORDING = 1,
    STATE_PAUSED,
    STATE_STOPPED,
    STATE_SAVE,
    STATE_SAVING,
    STATE_EXIT,
    STATE_EXITING
} STATE_VALUES;

typedef enum {
    STATUS_ERROR = 1,
    STATUS_SUCCESS_WITH_FILE = 2,
    STATUS_SUCCESS_NO_FILE = 3
} CDV_PLUGIN_STATUS;

#define MIC_NO_PERMISSION @"This app does not have permission to use the microphone"

/************************************************************************************************************
 *      CDV Audio Navigation Controller
 ************************************************************************************************************/
@interface CDVRecorderNavigationController : UINavigationController
@end

/************************************************************************************************************
 *      CDVAudioRecorder - Initialisation point of the plugin, creates a Navigation Controller and Pushes
 *      the main audio recorder view controller on to it.
 ************************************************************************************************************/
#ifndef DEV_PLUGING
@interface CDVAudioRecorder : CDVPlugin <UINavigationControllerDelegate>
{
    BOOL _inUse;
}
@property BOOL inUse;

- (void)audioRecorder:(CDVInvokedUrlCommand*)command;
- (NSDictionary*)getMediaDictionaryFromPath:(NSString*)fullPath ofType:(NSString*)type;
@end
#endif

/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

/************************************************************************************************************
 *      AudioRecorder - ViewController for the Audio Recorder
 ************************************************************************************************************/
@interface CDVRecorderViewController : UIViewController <AVAudioRecorderDelegate>
{
    UIButton *_saveCancelButton;
    UIButton *_recorderButton;
    UIButton *_backButton;

    UITextField *_txtRecordingName;
    UILabel *_timeElapsedLabel;
    UIView *_viewAmplitude;
    UILabel *_fileSizeLabel;
    UILabel *_maxFileSizeLabel;
    UIScrollView *_scrollView;
    UIView *_subview;
    UIToolbar *_controlsToolbar;
    int _userFilterID;
    UIView *_viewSaving;
    UIView *_circle;

    UIView *_circlesView;

    CGFloat _value;
    CGFloat _segment;
    STATE_VALUES _currentState;
    NSMutableArray *_circles;
    CGFloat _fadeColor;
    CAShapeLayer *_lastCircleLayer;
    CAShapeLayer *_lastRingLayer;

    CDVAUDIOError _errorCode;
    NSString *_callbackId;
    NSNumber *_duration;
BOOL _isTimed;
#ifndef DEV_PLUGING
    CDVAudioRecorder *_audioRecorderCommand;
    CDVPluginResult *_pluginResult;
#endif
    NSString *_errorResultMessage;
    UIStatusBarStyle _previousStatusBarStyle;
}

@property(nonatomic, assign) int userFilterID;

@property (nonatomic, retain) IBOutlet UIButton *saveCancelButton;
@property (nonatomic, retain) IBOutlet UIButton *recorderButton;
@property (nonatomic, retain) IBOutlet UIButton *backButton;

@property (nonatomic, retain) IBOutlet UIView *viewSaving;
@property (nonatomic, retain) IBOutlet UIToolbar *controlsToolbar;
@property (nonatomic, retain) IBOutlet UIScrollView *scrollView;
@property (nonatomic, retain) IBOutlet UILabel *fileSizeLabel;
@property (nonatomic, retain) IBOutlet UILabel *maxFileSizeLabel;
@property (nonatomic, retain) IBOutlet UILabel *timeElapsedLabel;
@property (nonatomic, retain) IBOutlet UITextField *txtRecordingName;
@property (nonatomic, retain) IBOutlet UIView *viewAmplitude;
@property (nonatomic, retain) IBOutlet UIView *subview;
@property (nonatomic, retain) IBOutlet UIView *circle;
@property (nonatomic, retain) IBOutlet UIView *circlesView;
@property (nonatomic, retain) CAShapeLayer *lastCircleLayer;
@property (nonatomic, retain) CAShapeLayer *lastRingLayer;

@property CGFloat value;
@property CGFloat segment;
@property STATE_VALUES currentState;
@property NSMutableArray *circles;

@property CGFloat fadeColor;

@property (nonatomic) CDVAUDIOError errorCode;
@property (nonatomic, copy) NSString *callbackId;
@property (nonatomic, copy) NSNumber *duration;
@property BOOL isTimed;
@property UIStatusBarStyle previousStatusBarStyle;

#ifndef DEV_PLUGING
@property (nonatomic, strong) CDVAudioRecorder *audioRecorderCommand;
@property (nonatomic, strong) CDVPluginResult *pluginResult;
#endif
@property (nonatomic, retain) NSString *errorResultMessage;

- (IBAction)recorderButtonPressed:(id)sender;
- (IBAction)backButtonPressed:(id)sender;
- (IBAction)saveButtonPressed:(id)sender;

#ifndef DEV_PLUGING
- (id)initWithCommand:(CDVPlugin *)theCommand duration:(NSNumber*)theDuration callbackId:(NSString*)theCallbackId;
#endif
-(void)finishPlugin;
-(void)finishPlugin_Error;
@end
