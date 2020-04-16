import Flutter
import UIKit
import AVFoundation
import MediaPlayer
import os

public class SwiftNativeAudioPlugin: NSObject, FlutterPlugin {
    
    private static let channelName = "com.danielgauci.native_audio"
    private let flutterMethodOnLoaded = "onLoaded"
    private let flutterMethodOnLoadedArgDuration = "duration"
    private let flutterMethodOnProgressChanged = "onProgressChanged"
    private let flutterMethodOnProgressChangedArgCurrentTime = "currentTime"
    private let flutterMethodOnResumed = "onResumed"
    private let flutterMethodOnPaused = "onPaused"
    private let flutterMethodOnStopped = "onStopped"
    private let flutterMethodOnCompleted = "onCompleted"
    private let flutterMethodOnError = "onError"
    
    private var flutterController : FlutterViewController!
    private var flutterChannel: FlutterMethodChannel!
    
    private var avPlayer: AVPlayer!
    private var avPlayerItem: AVPlayerItem!
    private var timeObserverToken: Any?
    private var playerItemContext = 0
    private var currentProgressInMillis = -1
    private var totalDurationInMillis = -1
    private var skipForwardTimeInMillis = 30_000
    private var skipBackwardTimeInMillis = 15_000
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: channelName, binaryMessenger: registrar.messenger())
        let instance = SwiftNativeAudioPlugin(withChannel: channel)
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public init(withChannel channel: FlutterMethodChannel) {
        super.init()
        flutterChannel = channel
        setupRemoteTransportControls()
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        result("iOS " + UIDevice.current.systemVersion)
        switch(call.method) {
        case "play":
            let arguments = call.arguments as! NSDictionary
            let url =  arguments["url"] as! String
            let title =  arguments["title"] as! String
            let artist =  arguments["artist"] as! String
            let album =  arguments["album"] as! String
            let imageUrl =  arguments["imageUrl"] as! String
            
            self.play(url: url, title: title, artist: artist, album: album, imageUrl: imageUrl)
            
        case "resume":
            self.resume()
            
        case "pause":
            self.pause()
            
        case "stop":
            self.stop()
            
        case "seekTo":
            let arguments = call.arguments as! NSDictionary
            let timeInMillis =  arguments["timeInMillis"] as! Int
            self.seekTo(timeInMillis: timeInMillis)
            
        default:
            self.log(message: "Unknown method called on Native Audio Player channel.")
        }
    }
    
    override public func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        // Only handle observations for the playerItemContext
        guard context == &playerItemContext else {
            super.observeValue(forKeyPath: keyPath,
                               of: object,
                               change: change,
                               context: context)
            return
        }
        
        if keyPath == #keyPath(AVPlayerItem.status) {
            let status: AVPlayerItem.Status
            if let statusNumber = change?[.newKey] as? NSNumber {
                status = AVPlayerItem.Status(rawValue: statusNumber.intValue)!
            } else {
                status = .unknown
            }
            
            switch status {
            case .readyToPlay:
                // Update listener
                let duration = avPlayerItem.duration
                let durationInSeconds = CMTimeGetSeconds(duration)
                totalDurationInMillis = Int(1000 * durationInSeconds)
                
                flutterChannel.invokeMethod(flutterMethodOnLoaded, arguments: Int(totalDurationInMillis))
                
                // Update control center
                MPNowPlayingInfoCenter.default().nowPlayingInfo![MPMediaItemPropertyPlaybackDuration] = CMTimeGetSeconds(avPlayerItem.duration)
                
            case .failed:
                log(message: "Failed AVPlayerItem state.")
                flutterChannel.invokeMethod(flutterMethodOnError, arguments: String("error"))
            case .unknown:
                log(message: "Unknown AVPlayerItem state.")
            }
        }
    }
    
    private func play(url: String, title: String, artist: String, album: String, imageUrl: String) {
        // Pause any ongoing playback and clean up resources. stop() is not called since we do not want to notify the Flutter channel
        if (avPlayer != nil) {avPlayer.pause()}
        cleanUp()
        
        // Update control center
        updateNowPlayingInfoCenter(title: title, artist: artist, album: album, imageUrl: imageUrl)
        
        // Setup player item
        guard let audioUrl = URL.init(string: url) else { return }
        avPlayerItem = AVPlayerItem.init(url: audioUrl)
        
        // Observe player item status
        avPlayerItem.addObserver(self, forKeyPath: #keyPath(AVPlayerItem.status), options: [.old, .new], context: &playerItemContext)
        
        // Setup player
        avPlayer = AVPlayer.init(playerItem: avPlayerItem)
        if #available(iOS 10, *){
            // Skips initial buffering
            avPlayer.automaticallyWaitsToMinimizeStalling = false
        }
        
        avPlayer.play()
        
        // Observe finished playing
        NotificationCenter.default.addObserver(self, selector: #selector(self.playerDidFinishPlaying(notification:)), name: NSNotification.Name.AVPlayerItemDidPlayToEndTime, object: avPlayerItem)
        
        // Set audio session as active to play in background
        do {
            try AVAudioSession.sharedInstance().setCategory(AVAudioSession.Category.playback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Failed to set AVAudioSession to active")
        }
        
        // Add progress listener
        let interval = CMTime(seconds: 1, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        let mainQueue = DispatchQueue.main
        
        timeObserverToken = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: mainQueue) { [weak self] time in
            let currentSeconds = CMTimeGetSeconds(time)
            let currentMillis = 1000 * currentSeconds
            
            self?.progressChanged(timeInMillis: Int(currentMillis))
        }
    }
    
    private func resume() {
        if let player = avPlayer {
            player.play()
            if player.currentItem != nil {
                MPNowPlayingInfoCenter.default().nowPlayingInfo![MPNowPlayingInfoPropertyElapsedPlaybackTime] = CMTimeGetSeconds(player.currentTime())
                MPNowPlayingInfoCenter.default().nowPlayingInfo![MPNowPlayingInfoPropertyPlaybackRate] = 1
            }
        }
        
        flutterChannel.invokeMethod(flutterMethodOnResumed, arguments: "")
    }
    
    private func pause() {
        if let player = avPlayer {
            player.pause()
            if player.currentItem != nil {
                MPNowPlayingInfoCenter.default().nowPlayingInfo![MPNowPlayingInfoPropertyElapsedPlaybackTime] = CMTimeGetSeconds(avPlayer.currentTime())
                MPNowPlayingInfoCenter.default().nowPlayingInfo![MPNowPlayingInfoPropertyPlaybackRate] = 0
            }
        }
        
        flutterChannel.invokeMethod(flutterMethodOnPaused, arguments: "")
    }
    
    private func stop() {
        do {
            // Set audio session as inactive
            try AVAudioSession.sharedInstance().setActive(false)
        } catch {
            print("Failed to set AVAudioSession to inactive")
        }
        
        if let player = avPlayer {
            player.pause()
            player.seek(to: CMTimeMake(value: 0, timescale: 1))
        }
        
        cleanUp()
        
        flutterChannel.invokeMethod(flutterMethodOnStopped, arguments: "")
    }
    
    private func skipForward() -> Bool {
        if (totalDurationInMillis > currentProgressInMillis + skipForwardTimeInMillis) {
            // Episode is loaded and there is enough time to skip forward
            seekTo(timeInMillis: currentProgressInMillis + skipForwardTimeInMillis)
            return true
        } else {
            print("Unable to skip forward, episode is not loaded or there is not enough time to skip forward")
            return false
        }
    }
    
    private func skipBackward() -> Bool {
        if (currentProgressInMillis - skipBackwardTimeInMillis > 0) {
            // Episode is loaded and there is enough time to skip backward
            seekTo(timeInMillis: currentProgressInMillis - skipBackwardTimeInMillis)
            return true
        } else {
            print("Unable to skip backward, episode is not loaded or there is not enough time to skip backward")
            return false
        }
    }
    
    private func seekTo(timeInMillis: Int) {
        if let player = avPlayer {
            // Playback is not automatically paused when seeking, handle this manually
            let isPlaying = player.rate > 0.0
            if (isPlaying) {pause()}
            
            let time = CMTimeMakeWithSeconds(Float64(timeInMillis / 1000), preferredTimescale: CMTimeScale(NSEC_PER_SEC))
            player.seek(to: time, completionHandler: { success in
                // Update Flutter progress
                self.flutterChannel.invokeMethod(self.flutterMethodOnProgressChanged, arguments: timeInMillis)

                // Resume playback if player was previously playing
                if (isPlaying){
                    self.resume()
                }
                
                // Update info center
                if player.currentItem != nil {
                    MPNowPlayingInfoCenter.default().nowPlayingInfo![MPNowPlayingInfoPropertyElapsedPlaybackTime] = Float64(timeInMillis / 1000)
                }
            })
        }
    }
    
    private func setupRemoteTransportControls() {
        // Get the shared MPRemoteCommandCenter
        let commandCenter = MPRemoteCommandCenter.shared()
        
        // Add handler for Play Command
        commandCenter.playCommand.addTarget { [unowned self] event in
            self.resume()
            return .success
        }
        
        // Add handler for Pause Command
        commandCenter.pauseCommand.addTarget { [unowned self] event in
            self.pause()
            return.success
        }
        
        // Disable next/previous track
        commandCenter.nextTrackCommand.addTarget { [unowned self] event in
            return self.skipForward() ? MPRemoteCommandHandlerStatus.success : MPRemoteCommandHandlerStatus.commandFailed
        }
        
        commandCenter.previousTrackCommand.addTarget { [unowned self] event in
            return self.skipBackward() ? MPRemoteCommandHandlerStatus.success : MPRemoteCommandHandlerStatus.commandFailed
        }
        
        if #available(iOS 9.1, *) {
            commandCenter.changePlaybackPositionCommand.isEnabled = true
            commandCenter.changePlaybackPositionCommand.addTarget { event in
                if let event = event as? MPChangePlaybackPositionCommandEvent {
                    let time = CMTime(seconds: event.positionTime, preferredTimescale: 1000000).seconds
                    self.seekTo(timeInMillis: Int(1000 * time))
                }
                return .success
            }
        } else {
            // Fallback on earlier versions
        }
    }
    
    private func updateNowPlayingInfoCenter(title: String, artist: String, album: String, imageUrl: String) {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyAlbumTitle: album,
            MPMediaItemPropertyArtist: artist,
        ]
        
        if let data = try? Data(contentsOf: URL(string: imageUrl)!) {
            let artwork: UIImage? = UIImage(data: data)!
            
            if #available(iOS 10.0, *) {
                if let artwork = artwork {
                    MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyArtwork] = MPMediaItemArtwork.init(boundsSize: artwork.size, requestHandler: { (size) -> UIImage in
                        return artwork
                    })
                }
            }
        }
    }
    
    private func progressChanged(timeInMillis: Int) {
        currentProgressInMillis = timeInMillis
        flutterChannel.invokeMethod(flutterMethodOnProgressChanged, arguments: timeInMillis)
    }
    
    @objc func playerDidFinishPlaying(notification: Notification) {
        flutterChannel.invokeMethod(flutterMethodOnCompleted, arguments: "")
    }
    
    private func cleanUp() {
        // Cleanup player
        if let player = avPlayer {
            if let timeObserverToken = timeObserverToken {
                player.removeTimeObserver(timeObserverToken)
                self.timeObserverToken = nil
            }
            
            avPlayer = nil
        }
       
        // Cleanup player item
        if let playerItem = avPlayerItem {
           playerItem.removeObserver(self, forKeyPath: #keyPath(AVPlayerItem.status))
            avPlayerItem = nil
        }
        
        // Remove notification center observers
        NotificationCenter.default.removeObserver(self)
    }
    
    private func log(message: StaticString) {
        if #available(iOS 10.0, *) {
            os_log(message)
        }
    }
}
