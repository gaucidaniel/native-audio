## 0.4.0

- Add support for the latest Flutter version

## 0.3.0

- BREAKING: Migrate to (v2 Android embedding)[https://github.com/flutter/flutter/wiki/Upgrading-pre-1.12-Android-projects]

## 0.2.10

- Add `isLocal` parameter to the `play` method. While this parameter is not required for local playback on Android, it must be set for iOS and as such it is always recommended to be set when playing local files.

## 0.2.9

- Fix remaining issues with seeking when paused on iOS

## 0.2.8

- Fix issue with seeking forward and backward when paused on iOS

## 0.2.7

- Fix incorrect notification state when `startAutomatically=false`

## 0.2.6

- Fix incorrect playback state showing in notification on Android

## 0.2.5

- Fix Android build issues introduced with the move to SDK 30

## 0.2.4

- Update Android compileSdkVersion to 30

## 0.2.3

- Fix Android audio focus issues when `startAutomatically=false`

## 0.2.2

- Fix audio stopping rather than pausing when some bluetooth devices are switched off

## 0.2.1

- Stop calling `onResumed` as soon as the audio loads and starts playing if `startAutomatically=true` (introduced in 0.2.0), and instead return `startedAutomatically` as a parameter of `onLoaded`.

## 0.2.0

- Adds `startAutomatically` and `startFrom` fields to play function. Note that as of this version, if `startAutomatically=true`, `onResumed` is now called as soon as the audio loads and starts playing.

## 0.1.0

- Allow skipping forward and backward near the start and end of the stream respectively. For example, with the new behavior when only 5 seconds remain from the stream and the user attempts to skip forward (with a skip time larger than 5 seconds), the stream will end, whereas with previous versions the skip would be ignored. Should the previous behavior still be desired, it should be implemented within the hosting application.

## 0.0.25

- Fix onProgressChanged not called when seeking while playback is paused on Android

## 0.0.24

- Fix onProgressChanged called before onLoaded on iOS

## 0.0.23

- Fix notification not being dismissed on Android when playback is complete

## 0.0.22

- Fix skip forward and skip backward not working on Android

## 0.0.21

- Update changelog and readme

## 0.0.20

- Add configurable skip forward and backward times (used for notification, skipForward() and skipBackward())
- Include Android manifest changes within the library's manifest to simplify installation. Existing native-audio permissions, services and broadcast receivers can now be removed from the app's manifest.
- Create overridable notification icon for Android. Simply add a drawable named `native_audio_notification_icon` to your drawables to override the notification icon.
- Pause audio instead of stopping when audio focus is lost on Android
- Fix inconsistent play/pause button on Bluetooth devices on Android
- Update media notification styling on Android

## 0.0.19

- Stop onPause and onResumed from being called when seeking on iOS

## 0.0.18

- Support audio interruptions on iOS
- Fix seeking precision on iOS
- Fix build warnings on iOS
- Support latest Flutter version for Example app

## 0.0.17

- Fixed crashes when calling stop() while the audio is already stopped

## 0.0.16

- Unregister listeners when stopping or playing a new audio URL on iOS

## 0.0.15

- Pause playback during seeking on iOS

## 0.0.14

- Fix iOS notification seek bar
- Android Notification fixes and improvements
- Improve ReadMe with prerequisites

## 0.0.13

- Register method call handler on play instead of on initialization to ensure that the channel has not been taken over since initialization. This also opens up the possibility of having multiple instances of NativeAudio.

## 0.0.12

- [Android] Support Android 10 media notification seek bar, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]
- [Android] Support Samsung metadata, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]

## 0.0.11

- Fix changelog formatting

## 0.0.10

- [Android] Allow Media Controls show in compact notification, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]
- [iOS] Improve loading times on iOS 10 and newer, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]
- [iOS] Show artwork in Command Center, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]

## 0.0.9

- [Android] Optimize progress updates

## 0.0.8

- [Android] Fix MediaPlayer not being released [Android]

## 0.0.7

- [Android] Properly handle Audio Focus
- [Android] Pause playback when Bluetooth device disconnected
- [Android] Pause playback when headset disconnected
- [Android] Bump minSdk to 21

## 0.0.6

- Fixed issue causing playback not to stop when Android notification is dismissed

## 0.0.5

- Fixed issue causing Android notification to re-appear when closed

## 0.0.4

- Initial iOS support

## 0.0.3

- Change package name from studio.darngood to com.danielgauci

## 0.0.2

- Fix NPE due to incorrect context
- Throw exception if pluginRegistrantCallback is not set
- Clean up unused constants

## 0.0.1

- Initial release
