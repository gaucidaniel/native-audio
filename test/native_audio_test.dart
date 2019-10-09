import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:native_audio/native_audio.dart';

void main() {
  const MethodChannel channel = MethodChannel('com.danielgauci.native_audio');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return true;
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
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
