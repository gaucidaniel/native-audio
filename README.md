# Native Audio

An audio plugin for Flutter which makes use of the native Android and iOS audio players, handling playback, notifications and external controls (such as bluetooth).

## Getting Started

This plugin works on both Android and iOS, however the following setup is required for each platform.

### Android

#### Prerequisite

- Flutter Project MinSDK 21
- [Flutter Project AndroidX](https://flutter.dev/docs/development/androidx-migration#how-do-i-migrate-my-existing-app-plugin-or-host-editable-module-project-to-androidx)

#### 1. Application

Create or modify the `Application` class as follows:

```kotlin
import io.flutter.app.FlutterApplication
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugins.GeneratedPluginRegistrant
import com.danielgauci.native_audio.NativeAudioPlugin

class Application : FlutterApplication(), PluginRegistry.PluginRegistrantCallback {

    override fun onCreate() {
        super.onCreate()
        NativeAudioPlugin.setPluginRegistrantCallback(this)
    }

    override fun registerWith(registry: PluginRegistry) {
        GeneratedPluginRegistrant.registerWith(registry)
    }
}
```

This must be reflected in the application's `AndroidManifest.xml`. E.g.:

```xml
    <application
        android:name=".Application"
        ...
```

**Note:** Not calling `NativeAudioPlugin.setPluginRegistrant` will result in an exception being
thrown when audio is played.

### iOS

#### Prerequisite

- Flutter Project minimum `iOS 10`
- Flutter Project minimum build `Swift 4.2`

No additional setup is required for iOS 🍏



## Customization

#### Notification Icon

Simply add a drawable named `native_audio_notification_icon` to your drawables to override the notification icon. This can be in .xml or in any other Android supported format.

Note: This is only useful for Android. On iOS, the launcher icon will always be used.

#### Setting skip times

Setting the skip times controls the time that will be skipped forward/backward when `skipForward()`, `skipBackward()` or the media icons in the notification are called.

```
_audio.setSkipTime(forwardTime: Duration(seconds: 30), backwardTime: Duration(seconds: 10));
```