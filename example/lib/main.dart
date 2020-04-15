import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:native_audio/native_audio.dart';
import 'package:rxdart/rxdart.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  var _audio = NativeAudio();
  var _progressText = "0:00:00";
  var _status = "stopped";

  @override
  void initState() {
    super.initState();
    checkStatus();
  }
  void checkStatus() {
    _audio.checkStatus((val){
      print(val);
    });
  }
  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create:(context) =>  _audio,
          child: MaterialApp(
        home: Scaffold(
          appBar: AppBar(
            title: const Text('Native Audio'),
          ),
          body: Builder(
                      builder: (context) =>  Consumer<NativeAudio>(
              builder: (context, state, child) =>  Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  Text(_progressText, textAlign: TextAlign.center),
                  Padding(
                    padding: EdgeInsets.all(16.0),
                    child: Text(_status, textAlign: TextAlign.center),
                  ),
                  SizedBox(height: 48),
                  (state.value?.isBuffering ?? false) ?
                  Center(child: SizedBox( child: CircularProgressIndicator(strokeWidth: 1,)))
                  : PlayPauseButton(isPlaying: state.value?.isPlaying,  onPressed: () =>
                  !(state.value?.isReady ?? false) ?_playSampleAudio() :
                   (state.value?.isPlaying ?? false)?  _audio.pause() :_audio.resume()),
                  if  (state.value?.isReady ?? false)IconButton(icon:Icon(Icons.stop), onPressed: () => _audio.stop()),
                  if  (state.value?.isReady ?? false)
                    MaterialButton(child: Text("Seek to 30m"), onPressed: () => _audio.seekTo(Duration(minutes: 30))),
                  if (state.value?.isReady ?? false)
                    MaterialButton(child: Text("Seek to 70m"), onPressed: () => _audio.seekTo(Duration(minutes: 70))),
                    ProgressIndicator()
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
  void _playSampleAudio() {print('play');
     
    _audio.play(
      // "https://file-examples.com/wp-content/uploads/2017/11/file_example_MP3_700KB.mp3",
    'https://s3.amazonaws.com/scifri-episodes/scifri20181123-episode.mp3',
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



class PlayPauseButton extends StatefulWidget {
  final bool isPlaying;
  final VoidCallback onPressed;
  const PlayPauseButton({Key key, this.isPlaying, this.onPressed}) : super(key: key);
  @override
  _PlayPauseButtonState createState() => _PlayPauseButtonState();
}

class _PlayPauseButtonState extends State<PlayPauseButton>
    with SingleTickerProviderStateMixin {
  AnimationController _controller;
  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: Duration(milliseconds: 400), lowerBound: 0, upperBound: 1);
  }
  @override
  void didChangeDependencies() {
    if(widget.isPlaying)_controller.forward();
   else _controller.reverse();
    super.didChangeDependencies();
    print('did change dependencies');
  }

   void _playPauseListener() {
    if (widget.isPlaying) {
      if (_controller.status.index < 3 && !_controller.isAnimating)
        _controller.forward();
    } else {
      if (_controller.status.index > 1 && !_controller.isAnimating)
        _controller.reverse();
    }
  }

  @override
  void dispose() {
    super.dispose();
    _controller.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // print('build play button ${widget.isPlaying}');
     _playPauseListener();
    return AnimatedBuilder(animation: _controller, builder: (context, child) => 
              IconButton(icon: AnimatedIcon(icon: AnimatedIcons.play_pause, progress: _controller),
              onPressed: widget.onPressed));
}
}


class ProgressIndicator extends StatefulWidget {
  @override
  _VideoControlsState createState() => _VideoControlsState();
}

class _VideoControlsState extends State<ProgressIndicator> {
  final BehaviorSubject<String> _durationSubject =
      BehaviorSubject.seeded('0.0');
  final BehaviorSubject<String> _positionSubject =
      BehaviorSubject.seeded('0.0');

NativeAudio _audioController;
  double _slidePosition;

@override
  void didChangeDependencies() {
   _audioController = Provider.of<NativeAudio>(context);
   _audioController.addListener(_positionListener);
    super.didChangeDependencies();
  }
  void _positionListener() {
    _positionSubject.add(
        _formatDuration(_audioController?.value?.position?.inMilliseconds) ??
            '0.0');
    _durationSubject.add(
        _formatDuration(_audioController?.value?.duration?.inMilliseconds) ??
            '');
  }

  @override
  void didUpdateWidget(ProgressIndicator oldWidget) {
    super.didUpdateWidget(oldWidget);
    _audioController?.removeListener(_positionListener);
   _audioController?.addListener(_positionListener);
  }

  @override
  void deactivate() {
    super.deactivate();
    _audioController?.removeListener(_positionListener);
  }

  @override
  void dispose() {
    _audioController?.removeListener(_positionListener);
    super.dispose();
  }



  @override
  Widget build(BuildContext context) {
    print('build ');

    return Container(
      padding: EdgeInsets.only(left: 5, right: 5),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: <Widget>[
          StreamBuilder(
              stream: _positionSubject.stream,
              builder: (context, snapshot) => Text(
                  snapshot.data ?? '0.0',
                  style: Theme.of(context).textTheme.caption.copyWith(
                       
                      ))),
                      Expanded(
              child: SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: Theme.of(context).accentColor,
              inactiveTrackColor:
                  Theme.of(context).accentColor.withOpacity(0.6),
              trackShape: RoundedRectSliderTrackShape(),
              trackHeight: 1.0,
              thumbShape: RoundSliderThumbShape(enabledThumbRadius: 10.0),
              thumbColor: Theme.of(context).accentColor,
              overlayColor: Colors.red.withAlpha(32),
              showValueIndicator: ShowValueIndicator.always,
              overlayShape: RoundSliderOverlayShape(overlayRadius: 28.0),
              tickMarkShape: RoundSliderTickMarkShape(),
              activeTickMarkColor: Colors.red[700],
              inactiveTickMarkColor: Colors.red[100],
              valueIndicatorShape: PaddleSliderValueIndicatorShape(),
              valueIndicatorColor: Theme.of(context).accentColor,
              valueIndicatorTextStyle: TextStyle(
                color: Colors.white,
              ),
            ),
            child: Slider(
              value: _audioController.value?.position?.inMilliseconds?.toDouble(),
              max:  _audioController.value?.duration?.inMilliseconds?.toDouble() ?? 0,
              min: 0,
              label:  _formatDuration(( _slidePosition?.toInt() ??   0) ??
            '0.0'),
              onChangeEnd: (value) {
                //  setState(() {
                _slidePosition = null;
                //  });
              },
              onChanged: (value) {
                print('changed $value');
                setState(() {
                  _slidePosition = value;
                });
                 _audioController.seekTo(Duration(milliseconds: value.toInt()));
              },
            ),
          )),
          StreamBuilder(
              stream: _durationSubject.stream,
              builder: (context, snapshot) => Text(snapshot.data ?? '',
                  style: Theme.of(context)
                      .textTheme
                      .caption
                      .copyWith())),
                                           
        ],
      ),
    );
    // Chewie(controller: _playerController);
  }

  String _formatDuration(int milliSeconds) {
    if (milliSeconds == null) return null;
    int seconds = milliSeconds ~/ 1000;
    final int hours = seconds ~/ 3600;
    seconds = seconds % 3600;
    var minutes = seconds ~/ 60;
    seconds = seconds % 60;
    final hoursString = hours >= 10 ? '$hours' : hours == 0 ? '00' : '0$hours';
    final minutesString =
        minutes >= 10 ? '$minutes' : minutes == 0 ? '00' : '0$minutes';
    final secondsString =
        seconds >= 10 ? '$seconds' : seconds == 0 ? '00' : '0$seconds';
    final formattedTime =
        '${hoursString == '00' ? '' : hoursString + ':'}$minutesString:$secondsString';
    return formattedTime;
  }
}

