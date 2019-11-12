import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:native_audio/native_audio.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  var _audio = NativeAudio();
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
            Padding(
              padding: EdgeInsets.all(16.0),
              child: Text(_status, textAlign: TextAlign.center),
            ),
            if (!_isLoaded) MaterialButton(child: Text("Play"), onPressed: () => _playSampleAudio()),
            if (_isLoaded) MaterialButton(child: Text("Stop"), onPressed: () => _audio.stop()),
            if (!_isPlaying) MaterialButton(child: Text("Resume"), onPressed: () => _audio.resume()),
            if (_isPlaying) MaterialButton(child: Text("Pause"), onPressed: () => _audio.pause()),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: <Widget>[
                MaterialButton(child: Text("_playSampleAudio"), onPressed: () => _playSampleAudio()),
                MaterialButton(child: Text("_playSampleAudio2"), onPressed: () => _playSampleAudio2()),
              ],
            )
            
          ],
        ),
      ),
    );
  }

  void _listenForAudioEvents() {
    _audio.onLoaded = (audioDuration) {
      setState(() {
        _isLoaded = true;
        _isPlaying = true;
        _status = "loaded";
      });
    };

    _audio.onResumed = () {
      setState(() => _isPlaying = true);
      _status = "resumed";
    };

    _audio.onPaused = () {
      setState(() {
        _isPlaying = false;
        _status = "paused";
      });
    };

    _audio.onPrevious = () {
      setState(() {
        _status = "previous";
      });
    };

    _audio.onNext = () {
      setState(() {
        _status = "next";
      });
    };

    _audio.onStopped = () {
      setState(() {
        _isLoaded = false;
        _isPlaying = false;
        _status = "stopped";
      });
    };

    _audio.onCompleted = () {
      setState(() {
        _isLoaded = false;
        _isPlaying = false;
        _status = "completed";
      });
    };
  }

  void _playSampleAudio() {
    _audio.play("https://jatt.download/dren/music/data/Single_Track/201911/Viah/320/Viah_1.mp3/Viah.mp3",
        title: "Viah",
        album: "Viah",
        artist: "Surjit Khan",
        imageUrl: "https://riskyjatt.io/music/thumb/486972/Viah.jpg");
  }

  void _playSampleAudio2() {
    _audio.play("https://jatt.download/dren/music/data/Single_Track/201911/End/320/End_1.mp3/End.mp3",
        title: "End",
        album: "End",
        artist: "Raj Ranjodh",
        imageUrl: "https://riskyjatt.io/music/thumb/486925/End.jpg"
    );
  }
}
