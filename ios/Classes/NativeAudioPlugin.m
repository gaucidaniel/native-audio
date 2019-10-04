#import "NativeAudioPlugin.h"
#import <native_audio/native_audio-Swift.h>

@implementation NativeAudioPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftNativeAudioPlugin registerWithRegistrar:registrar];
}
@end
