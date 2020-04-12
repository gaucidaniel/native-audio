import 'package:flutter/material.dart';

import 'package:native_audio/native_audio.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  var _audio = NativeAudio();
  var _progressText = "0:00:00";
  var _isLoaded = false;
  var _isPlaying = false;
  var _status = "stopped";

  @override
  void initState() {
    super.initState();
    _listenForAudioEvents();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Native Audio'),
        ),
        body: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text(_progressText, textAlign: TextAlign.center),
            Padding(
              padding: EdgeInsets.all(16.0),
              child: Text(_status, textAlign: TextAlign.center),
            ),
            SizedBox(height: 48),
            // if (!_isLoaded) 
            MaterialButton(child: Text("Play"), onPressed: () => _playSampleAudio()),
            if (_isLoaded) MaterialButton(child: Text("Stop"), onPressed: () => _audio.stop()),
            if (!_isPlaying && _isLoaded) MaterialButton(child: Text("Resume"), onPressed: () => _audio.resume()),
            if (_isPlaying) MaterialButton(child: Text("Pause"), onPressed: () => _audio.pause()),
            if (_isLoaded)
              MaterialButton(child: Text("Seek to 30m"), onPressed: () => _audio.seekTo(Duration(minutes: 30))),
            if (_isLoaded)
              MaterialButton(child: Text("Seek to 70m"), onPressed: () => _audio.seekTo(Duration(minutes: 70))),
          ],
        ),
      ),
    );
  }

  void _listenForAudioEvents() {
    _audio.onLoaded = (audioDuration) {
      print('events: loaded $audioDuration');
      setState(() {
        _isLoaded = true;
        _isPlaying = true;
        _status = "loaded";
      });
    };

    _audio.onResumed = () {
      print('events: resumed');
      setState(() => _isPlaying = true);
      _status = "resumed";
    };

    _audio.onPaused = () {
      print('events: paused');
      setState(() {
        _isPlaying = false;
        _status = "paused";
      });
    };

    _audio.onStopped = () {
      print('events: stoped');
      setState(() {
        _isLoaded = false;
        _isPlaying = false;
        _status = "stopped";
      });
    };

    _audio.onCompleted = () {
      print('events: completed');
      setState(() {
        _isLoaded = false;
        _isPlaying = false;
        _status = "completed";
      });
    };

    _audio.onProgressChanged = (progress) {
      print('events: progress $progress');
      setState(() {
        this._progressText = _durationToString(progress);
      });
    };
  }

  void _playSampleAudio() {
    _audio.play("https://s3.amazonaws.com/scifri-episodes/scifri20181123-episode.mp3",
        title: "How The Fashion Industry Is Responding To Climate Change",
        album: "Science Friday",
        artist: "WNYC Studio",
        imageUrl: "https://www.sciencefriday.com/wp-content/uploads/2019/09/clothes-close-min.jpg");
  }

  String _durationToString(Duration duration) {
    String twoDigits(int n) {
      if (n >= 10) return "$n";
      return "0$n";
    }

    String twoDigitMinutes = twoDigits(duration.inMinutes.remainder(60));
    String twoDigitSeconds = twoDigits(duration.inSeconds.remainder(60));
    return "${twoDigits(duration.inHours)}:$twoDigitMinutes:$twoDigitSeconds";
  }
}
