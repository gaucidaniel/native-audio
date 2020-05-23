## 0.0.19

* Stop onPause and onResumed from being called when seeking on iOS

## 0.0.18

* Support audio interruptions on iOS
* Fix seeking precision on iOS
* Fix build warnings on iOS
* Support latest Flutter version for Example app

## 0.0.17

* Fixed crashes when calling stop() while the audio is already stopped

## 0.0.16

* Unregister listeners when stopping or playing a new audio URL on iOS

## 0.0.15

* Pause playback during seeking on iOS

## 0.0.14

* Fix iOS notification seek bar
* Android Notification fixes and improvements
* Improve ReadMe with prerequisites 

## 0.0.13

* Register method call handler on play instead of on initialization to ensure that the channel has not been taken over since initialization. This also opens up the possibility of having multiple instances of NativeAudio.

## 0.0.12

* [Android] Support Android 10 media notification seek bar, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]
* [Android] Support Samsung metadata, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]

## 0.0.11

* Fix changelog formatting

## 0.0.10

* [Android] Allow Media Controls show in compact notification, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]
* [iOS] Improve loading times on iOS 10 and newer, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]
* [iOS] Show artwork in Command Center, fix by (YaarPatandarAA)[https://github.com/YaarPatandarAA]

## 0.0.9

* [Android] Optimize progress updates

## 0.0.8

* [Android] Fix MediaPlayer not being released [Android]

## 0.0.7

* [Android] Properly handle Audio Focus
* [Android] Pause playback when Bluetooth device disconnected
* [Android] Pause playback when headset disconnected
* [Android] Bump minSdk to 21

## 0.0.6

* Fixed issue causing playback not to stop when Android notification is dismissed

## 0.0.5

* Fixed issue causing Android notification to re-appear when closed

## 0.0.4

* Initial iOS support

## 0.0.3

* Change package name from studio.darngood to com.danielgauci

## 0.0.2

* Fix NPE due to incorrect context
* Throw exception if pluginRegistrantCallback is not set
* Clean up unused constants

## 0.0.1

* Initial release
