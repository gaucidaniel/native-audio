import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:native_audio/native_audio.dart';

void main() {
  const MethodChannel channel = MethodChannel('com.danielgauci.native_audio');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (message) async => true,
    );
  });

  test('play', () async {
    var audio = NativeAudio();
    audio.play(
      "some-url",
      title: "some-title",
      artist: "some-artist",
      album: "some-album",
      imageUrl: "some-image-url",
    );
  });
}
