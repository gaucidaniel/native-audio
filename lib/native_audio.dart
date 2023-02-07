import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

// enum Playertate {
//   loading,
// }
class NativeAudioValue {
  /// Constructs a audio with the given values. Only [duration] is required. The
  /// rest will initialize with default values when unset.
  NativeAudioValue({
    required this.duration,
    this.onCompletedAction,
    this.position = const Duration(),
    this.buffered = 0.0,
    this.isPlaying = false,
    this.isLooping = false,
    this.isReady = false,
    this.isBuffering = false,
    this.errorDescription,
  });

  /// Returns an instance with a `null` [Duration].
  NativeAudioValue.uninitialized()
      : this(
            duration: null,
            isPlaying: false,
            isReady: false,
            isBuffering: false);

  /// Returns an instance with a `null` [Duration] and the given
  /// [errorDescription].
  NativeAudioValue.erroneous(String errorDescription)
      : this(
            duration: null,
            errorDescription: errorDescription,
            isPlaying: false,
            isReady: false,
            isBuffering: false);

  /// The total duration of the audio.
  ///
  /// Is null when [initialized] is false.
  final Duration? duration;

  /// The current playback position.
  final Duration position;

  ///called when an audio is done playing,
  final Function? onCompletedAction;

  /// True if the audio is playing. False if it's paused.
  final bool isPlaying;

  /// True if the audio is looping.
  final bool isLooping;

  final bool isReady;

  /// True if the audio is currently buffering.
  final bool isBuffering;

  /// The current volume of the playback.
  final double buffered;

  /// A description of the error if present.
  ///
  /// If [hasError] is false this is [null].
  final String? errorDescription;

  /// Indicates whether or not the audio has been loaded and is ready to play.
  bool get initialized => duration != null;

  /// Indicates whether or not the audio is in an error state. If this is true
  /// [errorDescription] should have information about the problem.
  bool get hasError => errorDescription != null;

  /// Returns a new instance that has the same values as this current instance,
  /// except for any overrides passed in as arguments to [copyWidth].
  NativeAudioValue copyWith({
    Duration? duration,
    Duration? position,
    bool? isPlaying,
    bool? isLooping,
    bool? isBuffering,
    bool? isReady,
    double? volume,
    double? buffered,
    String? errorDescription,
  }) {
    return NativeAudioValue(
      duration: duration ?? this.duration,
      position: position ?? this.position,
      isPlaying: isPlaying ?? this.isPlaying,
      isLooping: isLooping ?? this.isLooping,
      isBuffering: isBuffering ?? this.isBuffering,
      isReady: isReady ?? this.isReady,
      buffered: buffered ?? this.buffered,
      errorDescription: errorDescription ?? this.errorDescription,
    );
  }

  @override
  String toString() {
    return '$runtimeType('
        'duration: $duration, '
        'position: $position, '
        'buffered: $buffered'
        'isReady: $isReady'
        'isPlaying: $isPlaying, '
        'isLooping: $isLooping, '
        'isBuffering: $isBuffering'
        'errorDescription: $errorDescription)';
  }
}

enum BufferState { MEDIA_INFO_BUFFERING_END, MEDIA_INFO_BUFFERING_START }

class NativeAudio extends ValueNotifier<NativeAudioValue> {
  static const _channel = const MethodChannel('com.danielgauci.native_audio');

  NativeAudio() : super(NativeAudioValue.uninitialized());
  StreamSubscription? _subscription;
  Function? onCompletionListener;
  bool _hasMethodHandler = false;
  void _initSub() {
    _subscription =
        Stream.periodic(Duration(milliseconds: 1000)).listen(_playistener);
  }

  void play(String url,
      {String? title,
      String? artist,
      String? album,
      String? imageUrl,
      bool isLooping = false}) async {
    if (!_hasMethodHandler) _registerMethodCallHandler();
    if (value.isPlaying) pause();
    _invokeNativeMethod(
      NATIVE_METHOD_PLAY,
      arguments: <String, dynamic>{
        NATIVE_METHOD_PLAY_ARG_URL: url,
        NATIVE_METHOD_PLAY_ARG_TITLE: title,
        NATIVE_METHOD_PLAY_ARG_ARTIST: artist,
        NATIVE_METHOD_PLAY_ARG_ALBUM: album,
        NATIVE_METHOD_PLAY_ARG_IMAGE_URL: imageUrl,
        NATIVE_METHOD_PLAY_ARG_LOOPING: isLooping ?? false
      },
    );
    value = value.copyWith(isBuffering: true);
  }

  void resume() {
    _invokeNativeMethod(NATIVE_METHOD_RESUME);
    //  value.copyWith(isPlaying: true);
  }

  void pause() async {
    await _invokeNativeMethod(NATIVE_METHOD_PAUSE);
    //  value.copyWith(isPlaying: false);
  }

  /// returns if the service is running in the background
  /// typically is called when the app is opened to verify
  /// if a media session is alredy active
  void checkStatus(Function(bool) handler) async {
    _channel.setMethodCallHandler((methodCall) async {
      if (methodCall.method == NATIVE_METHOD_SERVICE_STATUS) {
        bool isRunning = (methodCall.arguments as bool?) ?? false;
        handler(isRunning);
        if (isRunning) {
          _registerMethodCallHandler();
          // IF THE SERVICE IS ALREADY RUNNIG GET THE DURATION OF THE CURRENT TRACK
          _invokeNativeMethod(NATIVE_METHOD_GET_DURATION);
        }
      }
    });
    try {
      await _channel.invokeMethod(NATIVE_METHOD_SERVICE_STATUS, null);
    } on PlatformException catch (e) {
      handler(false);
    }
  }

  void stop() {
    _invokeNativeMethod(NATIVE_METHOD_STOP);
    value.copyWith(isReady: false, isBuffering: false, isPlaying: false);
  }

  void _onError(String? message) {
    String val = ERROR_CODES
        .firstWhere(
          (item) => (item.keys.toList()[0] == message),
          orElse: () => {
            'MEDIA_ERROR_UNKNOWN':
                'An unexpected error has occured, could not play audio'
          },
        )
        .values
        .toList()[0];
    value = NativeAudioValue.erroneous(val);
  }

  void seekTo(Duration time) {
    _invokeNativeMethod(
      NATIVE_METHOD_SEEK_TO,
      arguments: <String, dynamic>{
        NATIVE_METHOD_SEEK_TO_ARS_IN_MILLIS: time.inMilliseconds
      },
    );
    // _playistener();
  }

  int? _position;
  void _playistener([dynamic event]) {
    // print('play listener  $_position ${value?.position?.inSeconds}');
    if (value.isPlaying) {
      if (_position == value?.position?.inSeconds) {
        if (!value.isBuffering) value = value.copyWith(isBuffering: true);
      } else if (value.isBuffering) value = value.copyWith(isBuffering: false);
    } else if (value.isBuffering) value = value.copyWith(isBuffering: false);

    _position = value?.position?.inSeconds;
  }

  /// release the player, stop the audio service
  void release() {
    _invokeNativeMethod(NATIVE_METHOD_RELEASE);
    value = NativeAudioValue.uninitialized();
    _subscription?.cancel();
  }

  void _registerMethodCallHandler() {
    _hasMethodHandler = true;
    if (value == null) value = NativeAudioValue.uninitialized();
    _initSub();
    // Listen to method calls from native
    _channel.setMethodCallHandler((methodCall) async {
      // print('method call ${methodCall.method}');
      switch (methodCall.method) {
        case FLUTTER_METHOD_ON_LOADED:
          int? durationInMillis = methodCall.arguments;
          if (durationInMillis != null)
            value = value.copyWith(
                isReady: true,
                isPlaying: true,
                isBuffering: false,
                duration: Duration(
                  milliseconds: durationInMillis,
                ));
          break;

        case FLUTTER_METHOD_ON_RESUMED:
          value = value.copyWith(
            isPlaying: true,
            isReady: true,
          );
          _subscription?.resume();
          break;
        case FLUTTER_METHOD_ON_BUFFER_START:
          value = value.copyWith(isBuffering: true);
          break;
        case FLUTTER_METHOD_ON_BUFFER_END:
          value = value.copyWith(isBuffering: false);
          break;
        case FLUTTER_METHOD_ON_BUFFER_UPDATE:
          int? val = methodCall.arguments;
          if (val != null) {
            value = value.copyWith(buffered: val / 100);
          }

          break;
        case FLUTTER_METHOD_ON_DURATION:
          int? durationInMillis = methodCall.arguments;
          if (durationInMillis != null) {
            value = value.copyWith(
                isReady: true,
                duration: Duration(
                  milliseconds: durationInMillis,
                ));
          }

          break;
        case FLUTTER_METHOD_ON_PAUSED:
          value = value.copyWith(
            isPlaying: false,
          );
          _subscription?.pause();
          break;

        case FLUTTER_METHOD_ON_STOPPED:
          value = value.copyWith(
              isPlaying: false, isBuffering: false, isReady: false);
          _subscription?.pause();
          break;

        case FLUTTER_METHOD_ON_COMPLETED:
          value = value.copyWith(isPlaying: false, isBuffering: false);
          stop();
          if (onCompletionListener != null) onCompletionListener!();

          // _playistener();
          break;
        case FLUTTER_METHOD_ON_ERROR:
          String? message = methodCall.arguments;
          _onError(message);
          break;
        case FLUTTER_METHOD_ON_PROGRESS_CHANGED:
          int? currentTimeInMillis = methodCall.arguments;
          if (currentTimeInMillis != null)
            value = value.copyWith(
                position: Duration(milliseconds: currentTimeInMillis));
          break;
      }

      return;
    });
  }

  Future _invokeNativeMethod(String method,
      {Map<String, dynamic>? arguments}) async {
    try {
      await _channel.invokeMethod(method, arguments);
    } on PlatformException catch (e) {
      print("Failed to call native method: " + e.message!);
    }
  }
}

const MEDIA_INFO_BUFFERING_END = 702;
const MEDIA_INFO_BUFFERING_START = 701;

/// error codes
const ERROR_CODES = [
  {'MEDIA_ERROR_IO': 'There was a problem loading file '},
  {
    'MEDIA_ERROR_MALFORMED':
        'Bitstream is not conforming to the related coding standard or file spec.'
  },
  {'MEDIA_ERROR_SERVER_DIED': 'Media server error'},
  {
    'MEDIA_ERROR_UNKNOWN':
        'An unexpected error has occured, could not play audio'
  },
  {'MEDIA_ERROR_UNSUPPORTED': 'can not play the format of this file'}
];

/// status of the background service,
const NATIVE_METHOD_SERVICE_STATUS = "serviceStatus";

const NATIVE_METHOD_PLAY = "play";
const NATIVE_METHOD_PLAY_ARG_URL = "url";
const NATIVE_METHOD_PLAY_ARG_TITLE = "title";
const NATIVE_METHOD_PLAY_ARG_ARTIST = "artist";
const NATIVE_METHOD_PLAY_ARG_ALBUM = "album";
const NATIVE_METHOD_PLAY_ARG_IMAGE_URL = "imageUrl";
const NATIVE_METHOD_PLAY_ARG_LOOPING = "looping";
const NATIVE_METHOD_RESUME = "resume";
const NATIVE_METHOD_PAUSE = "pause";
const NATIVE_METHOD_STOP = "stop";
const NATIVE_METHOD_SEEK_TO = "seekTo";
const NATIVE_METHOD_SEEK_TO_ARS_IN_MILLIS = "timeInMillis";
const NATIVE_METHOD_RELEASE = "release";
const NATIVE_METHOD_GET_DURATION = "duration";

const FLUTTER_METHOD_ON_LOADED = "onLoaded";
const FLUTTER_METHOD_ON_RESUMED = "onResumed";
const FLUTTER_METHOD_ON_PAUSED = "onPaused";
const FLUTTER_METHOD_ON_STOPPED = "onStopped";
const FLUTTER_METHOD_ON_PROGRESS_CHANGED = "onProgressChanged";
const FLUTTER_METHOD_ON_COMPLETED = "onCompletedAction";
const FLUTTER_METHOD_ON_ERROR = "onError";
const FLUTTER_METHOD_ON_BUFFER_START = "onBufferingStart";
const FLUTTER_METHOD_ON_BUFFER_END = "onBufferingEnd";
const FLUTTER_METHOD_ON_BUFFER_UPDATE = "onBufferingUpdate";
const FLUTTER_METHOD_ON_DURATION = "onDuration";
