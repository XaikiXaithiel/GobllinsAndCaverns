import javax.sound.sampled.*;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class GameAudio {
    private final Consumer<String> errorLogger;
    private SourceDataLine narrationLine;
    private Thread narrationThread;
    private volatile Clip dialogueBoopClip;
    private volatile boolean dialogueBoopRequested;
    private volatile Clip buttonBoopClip;
    private volatile boolean buttonBoopRequested;
    private volatile Clip lightSpellClip;
    private volatile boolean lightSpellRequested;
    private final Map<String, Clip> soundEffects = new HashMap<>();
    private final Set<String> requestedSoundEffects = new HashSet<>();
    private volatile Clip homeMusicClip;
    private SourceDataLine caveAmbienceLine;
    private Thread caveAmbienceThread;
    private volatile boolean caveAmbiencePaused;
    private volatile boolean caveAmbienceStopping;
    private volatile long homeMusicFadeStartedAt;
    private volatile float homeMusicVolume = 0.7f;
    private volatile boolean muted;
    private boolean volumeWarningShown;
    private final Map<Line, Float> mutedLineGains = new IdentityHashMap<>();
    private long menuMusicRequest;
    private boolean menuMusicLoading;
    private volatile long caveAmbienceRequest;

    GameAudio(Consumer<String> errorLogger) {
        this.errorLogger = errorLogger;
        loadDialogueBoopAsync();
        loadButtonBoopAsync();
        loadLightSpellAsync();
        loadSoundEffectAsync("hpLost.wav");
        loadSoundEffectAsync("itemReveal.wav");
        loadSoundEffectAsync("caveReveal.wav");
        loadSoundEffectAsync("gameOver.wav");
        loadSoundEffectAsync("winnerSFX.wav");
        loadSoundEffectAsync("audioRustle.wav");
    }

    void playNarration(String path) {
        stopNarration();
        File file = GameAssets.resolveFile(path);
        if (file == null) {
            errorLogger.accept("Narration file not found. Working directory: "
                + System.getProperty("user.dir") + "; looking for: " + path);
            return;
        }
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = stream.getFormat();
            AudioFormat playFormat = format;
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(), 16, format.getChannels(),
                    format.getChannels() * 2, format.getSampleRate(), false);
                playFormat = pcm;
                stream = AudioSystem.getAudioInputStream(pcm, stream);
                info = new DataLine.Info(SourceDataLine.class, pcm);
            }
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(playFormat);
            line.start();
            narrationLine = line;
            applyMute(line);
            AudioInputStream input = stream;
            narrationThread = new Thread(() -> {
                byte[] data = new byte[4096];
                try {
                    int read;
                    while (line.isOpen() && (read = input.read(data, 0, data.length)) != -1) {
                        if (line == narrationLine) line.write(data, 0, read);
                        else break;
                    }
                } catch (Exception ignored) {
                } finally {
                    try { input.close(); } catch (Exception ignored) {}
                    line.close();
                    forgetMutedGain(line);
                }
            }, "theMage-narration");
            narrationThread.start();
            System.out.println("Playing narration: " + file);
        } catch (Exception e) {
            errorLogger.accept("Could not play narration: " + file + " - " + e.getMessage());
        }
    }

    synchronized void playMenuMusic() {
        if (homeMusicClip != null && homeMusicClip.isOpen()) return;
        if (menuMusicLoading) return;
        menuMusicLoading = true;
        long request = ++menuMusicRequest;
        Thread loader = new Thread(() -> loadMenuMusic(request), "menu-music-loader");
        loader.setDaemon(true);
        loader.start();
    }

    synchronized void playCaveAmbience() {
        if (caveAmbienceThread != null && caveAmbienceThread.isAlive()) {
            caveAmbienceStopping = false;
            caveAmbiencePaused = false;
            return;
        }
        caveAmbiencePaused = false;
        caveAmbienceStopping = false;
        long request = ++caveAmbienceRequest;
        caveAmbienceThread = new Thread(() -> streamCaveAmbience(request), "cave-ambience");
        caveAmbienceThread.setDaemon(true);
        caveAmbienceThread.start();
    }

    void pauseCaveAmbience(boolean pause) {
        caveAmbiencePaused = pause;
    }

    private void streamCaveAmbience(long request) {
        File file = GameAssets.resolveFile("audio/ambienceCave.wav");
        if (file == null) {
            errorLogger.accept("Cave ambience file not found. Working directory: "
                + System.getProperty("user.dir") + "; looking for: audio/ambienceCave.wav");
            return;
        }
        SourceDataLine line = null;
        AudioInputStream stream = null;
        try {
            AudioInputStream source = AudioSystem.getAudioInputStream(file);
            AudioFormat sourceFormat = source.getFormat();
            AudioFormat outputFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(),
                sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);
            stream = AudioSystem.getAudioInputStream(outputFormat, source);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, outputFormat);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(outputFormat, 4096);
            synchronized (this) {
                if (request != caveAmbienceRequest) return;
                caveAmbienceLine = line;
            }
            line.start();
            byte[] data = new byte[4096];
            float pauseFade = 1f;
            long positionBytes = 0;
            int frameSize = outputFormat.getFrameSize();
            float pauseFadeStep = 1f / (outputFormat.getSampleRate() * 0.8f);
            while (request == caveAmbienceRequest && line.isOpen()) {
                if (caveAmbiencePaused && pauseFade <= 0f) {
                    line.drain();
                    line.stop();
                    while (request == caveAmbienceRequest && caveAmbiencePaused) {
                        if (caveAmbienceStopping) break;
                        Thread.sleep(40);
                    }
                    if (request != caveAmbienceRequest || caveAmbienceStopping) break;
                    line.start();
                }
                int read = stream.read(data, 0, data.length);
                if (read == -1) {
                    stream.close();
                    stream = openAudioStream(file, outputFormat);
                    positionBytes = 0;
                    continue;
                }
                long frameLength = stream.getFrameLength();
                for (int offset = 0; offset + frameSize <= read; offset += frameSize) {
                    pauseFade = caveAmbiencePaused
                        ? Math.max(0f, pauseFade - pauseFadeStep)
                        : Math.min(1f, pauseFade + pauseFadeStep);
                    long framePosition = (positionBytes + offset) / frameSize;
                    float loopFade = 1f;
                    if (frameLength != AudioSystem.NOT_SPECIFIED) {
                        float elapsedSeconds = framePosition / outputFormat.getFrameRate();
                        float remainingSeconds = (frameLength - framePosition) / outputFormat.getFrameRate();
                        loopFade = Math.min(1f, Math.min(elapsedSeconds / 0.8f, remainingSeconds / 0.8f));
                    }
                    float gain = muted ? 0f : 0.4f * pauseFade * Math.max(0f, loopFade);
                    scalePcm16Frame(data, offset, frameSize, gain);
                }
                line.write(data, 0, read);
                positionBytes += read;
            }
        } catch (Exception e) {
            if (request == caveAmbienceRequest) {
                errorLogger.accept("Could not play cave ambience: " + file + " - " + e.getMessage());
            }
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Exception e) {
                    errorLogger.accept("Could not close cave ambience stream: " + e.getMessage());
                }
            }
            if (line != null) line.close();
            synchronized (this) {
                if (caveAmbienceLine == line) caveAmbienceLine = null;
                if (caveAmbienceThread == Thread.currentThread()) caveAmbienceThread = null;
                if (line != null) forgetMutedGain(line);
            }
        }
    }

    private AudioInputStream openAudioStream(File file, AudioFormat outputFormat) throws Exception {
        AudioInputStream stream = AudioSystem.getAudioInputStream(file);
        if (outputFormat.matches(stream.getFormat())) return stream;
        try {
            return AudioSystem.getAudioInputStream(outputFormat, stream);
        } catch (Exception e) {
            stream.close();
            throw e;
        }
    }

    private void scalePcm16Frame(byte[] data, int offset, int frameSize, float gain) {
        for (int i = offset; i + 1 < offset + frameSize; i += 2) {
            int sample = (short) ((data[i] & 0xff) | (data[i + 1] << 8));
            int scaled = Math.round(sample * gain);
            data[i] = (byte) scaled;
            data[i + 1] = (byte) (scaled >>> 8);
        }
    }

    synchronized void stopCaveAmbience() {
        caveAmbienceStopping = true;
        caveAmbiencePaused = true;
    }

    private void loadMenuMusic(long request) {
        File file = GameAssets.resolveFile("audio/theHome.wav");
        if (file == null) {
            finishMenuMusicLoad(request, null,
                "Menu music file not found. Working directory: "
                    + System.getProperty("user.dir") + "; looking for: audio/theHome.wav");
            return;
        }
        Clip clip = null;
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
            clip = AudioSystem.getClip();
            clip.open(stream);
            applyMute(clip);
            setMusicGain(clip, 0f);
            finishMenuMusicLoad(request, clip, null);
        } catch (Exception e) {
            if (clip != null) clip.close();
            finishMenuMusicLoad(request, null,
                "Could not play menu music: " + file + " - " + e.getMessage());
        }
    }

    private synchronized void finishMenuMusicLoad(long request, Clip clip, String error) {
        if (request != menuMusicRequest) {
            if (clip != null) clip.close();
            return;
        }
        menuMusicLoading = false;
        if (error != null) {
            errorLogger.accept(error);
            return;
        }
        homeMusicClip = clip;
        homeMusicFadeStartedAt = System.currentTimeMillis();
        clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    synchronized void stopMenuMusic() {
        menuMusicRequest++;
        menuMusicLoading = false;
        if (homeMusicClip != null) {
            homeMusicClip.stop();
            homeMusicClip.close();
            forgetMutedGain(homeMusicClip);
            homeMusicClip = null;
        }
        homeMusicFadeStartedAt = 0;
    }

    void updateMenuMusicFade() {
        if (homeMusicClip == null || !homeMusicClip.isOpen() || homeMusicFadeStartedAt == 0) return;
        float fade = Math.min(1f, (System.currentTimeMillis() - homeMusicFadeStartedAt) / 2000f);
        setMusicGain(homeMusicClip, homeMusicVolume * fade);
        if (fade >= 1f) homeMusicFadeStartedAt = 0;
    }

    void setMusicVolume(float volume) {
        homeMusicVolume = Math.max(0f, Math.min(1f, volume));
        if (homeMusicClip != null && homeMusicClip.isOpen()) {
            float fade = homeMusicFadeStartedAt == 0 ? 1f
                : Math.min(1f, (System.currentTimeMillis() - homeMusicFadeStartedAt) / 2000f);
            setMusicGain(homeMusicClip, homeMusicVolume * fade);
        }
    }

    float getMusicVolume() {
        return homeMusicVolume;
    }

    synchronized void toggleMute() {
        muted = !muted;
        if (narrationLine != null && narrationLine.isOpen()) applyMute(narrationLine);
        if (dialogueBoopClip != null && dialogueBoopClip.isOpen()) applyMute(dialogueBoopClip);
        if (buttonBoopClip != null && buttonBoopClip.isOpen()) applyMute(buttonBoopClip);
        if (lightSpellClip != null && lightSpellClip.isOpen()) applyMute(lightSpellClip);
        for (Clip clip : soundEffects.values()) {
            if (clip.isOpen()) applyMute(clip);
        }
        if (homeMusicClip != null && homeMusicClip.isOpen()) applyMute(homeMusicClip);
        if (caveAmbienceLine != null && caveAmbienceLine.isOpen()) applyMute(caveAmbienceLine);
    }

    boolean isMuted() {
        return muted;
    }

    private void loadDialogueBoopAsync() {
        Thread loader = new Thread(() -> {
            File file = GameAssets.resolveFile("audio/boopDialogue.wav");
            if (file == null) {
                errorLogger.accept("Dialogue sound file not found. Working directory: "
                    + System.getProperty("user.dir") + "; looking for: audio/boopDialogue.wav");
                return;
            }
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                setDialogueGain(clip);
                synchronized (this) {
                    dialogueBoopClip = clip;
                    applyMute(clip);
                    if (dialogueBoopRequested) {
                        dialogueBoopRequested = false;
                        startDialogueBoop(clip);
                    }
                }
            } catch (Exception e) {
                errorLogger.accept("Could not load dialogue sound: " + file + " - " + e.getMessage());
            }
        }, "dialogue-sound-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void loadButtonBoopAsync() {
        Thread loader = new Thread(() -> {
            File file = GameAssets.resolveFile("audio/boopButton.wav");
            if (file == null) {
                errorLogger.accept("Button sound file not found. Working directory: "
                    + System.getProperty("user.dir") + "; looking for: audio/boopButton.wav");
                return;
            }
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                setClipGain(clip, 0.5f, "button sound");
                synchronized (this) {
                    buttonBoopClip = clip;
                    applyMute(clip);
                    if (buttonBoopRequested && !muted) {
                        buttonBoopRequested = false;
                        startDialogueBoop(clip);
                    }
                }
            } catch (Exception e) {
                errorLogger.accept("Could not load button sound: " + file + " - " + e.getMessage());
            }
        }, "button-sound-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void loadLightSpellAsync() {
        Thread loader = new Thread(() -> {
            File file = GameAssets.resolveFile("audio/LightSpell.wav");
            if (file == null) {
                errorLogger.accept("Light spell sound file not found. Working directory: "
                    + System.getProperty("user.dir") + "; looking for: audio/LightSpell.wav");
                return;
            }
            try (AudioInputStream source = AudioSystem.getAudioInputStream(file)) {
                AudioFormat format = source.getFormat();
                AudioFormat playbackFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(), 16, format.getChannels(),
                    format.getChannels() * 2, format.getSampleRate(), false);
                try (AudioInputStream stream = AudioSystem.getAudioInputStream(playbackFormat, source)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(stream);
                    setClipGain(clip, 0.75f, "light spell sound");
                    synchronized (this) {
                        lightSpellClip = clip;
                        applyMute(clip);
                        if (lightSpellRequested && !muted) {
                            lightSpellRequested = false;
                            startDialogueBoop(clip);
                        }
                    }
                }
            } catch (Exception e) {
                errorLogger.accept("Could not load light spell sound: " + file + " - " + e.getMessage());
            }
        }, "light-spell-sound-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void loadSoundEffectAsync(String name) {
        Thread loader = new Thread(() -> {
            String path = "audio/" + name;
            File file = GameAssets.resolveFile(path);
            if (file == null) {
                errorLogger.accept("Sound effect file not found. Working directory: "
                    + System.getProperty("user.dir") + "; looking for: " + path);
                return;
            }
            try (AudioInputStream source = AudioSystem.getAudioInputStream(file)) {
                AudioFormat format = source.getFormat();
                AudioFormat playbackFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(), 16, format.getChannels(),
                    format.getChannels() * 2, format.getSampleRate(), false);
                try (AudioInputStream stream = AudioSystem.getAudioInputStream(playbackFormat, source)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(stream);
                    setClipGain(clip, 0.8f, name);
                    synchronized (this) {
                        soundEffects.put(name, clip);
                        applyMute(clip);
                        if (requestedSoundEffects.remove(name) && !muted) playEffect(clip);
                    }
                }
            } catch (Exception e) {
                errorLogger.accept("Could not load sound effect: " + file + " - " + e.getMessage());
            }
        }, "sound-effect-loader-" + name);
        loader.setDaemon(true);
        loader.start();
    }

    synchronized void playDialogueBoop() {
        Clip clip = dialogueBoopClip;
        if (clip == null || !clip.isOpen()) {
            dialogueBoopRequested = true;
            return;
        }
        startDialogueBoop(clip);
    }

    synchronized void playButtonBoop() {
        if (muted) return;
        Clip clip = buttonBoopClip;
        if (clip == null || !clip.isOpen()) {
            buttonBoopRequested = true;
            return;
        }
        buttonBoopRequested = false;
        startDialogueBoop(clip);
    }

    synchronized void playLightSpell() {
        if (muted) return;
        Clip clip = lightSpellClip;
        if (clip == null || !clip.isOpen()) {
            lightSpellRequested = true;
            return;
        }
        lightSpellRequested = false;
        if (clip.isRunning()) return;
        clip.setFramePosition(0);
        clip.start();
    }

    synchronized void playHpLost() {
        playSoundEffect("hpLost.wav");
    }

    synchronized void playItemReveal() {
        playSoundEffect("itemReveal.wav");
    }

    synchronized void playCaveReveal() {
        playSoundEffect("caveReveal.wav");
    }

    synchronized void playGameOver() {
        playSoundEffect("gameOver.wav");
    }

    synchronized void playWinner() {
        playSoundEffect("winnerSFX.wav");
    }

    synchronized void playRustle() {
        playSoundEffect("audioRustle.wav");
    }

    private void playSoundEffect(String name) {
        if (muted) return;
        Clip clip = soundEffects.get(name);
        if (clip == null || !clip.isOpen()) {
            requestedSoundEffects.add(name);
            return;
        }
        playEffect(clip);
    }

    private void playEffect(Clip clip) {
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    private void startDialogueBoop(Clip clip) {
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    void pauseNarration() {
        if (narrationLine != null && narrationLine.isRunning()) narrationLine.stop();
    }

    void resumeNarration() {
        if (narrationLine != null && narrationLine.isOpen()) {
            narrationLine.start();
        }
    }

    void stopNarration() {
        SourceDataLine line = narrationLine;
        narrationLine = null;
        if (line != null) {
            line.stop();
            line.close();
            forgetMutedGain(line);
        }
        Thread thread = narrationThread;
        narrationThread = null;
        if (thread != null) thread.interrupt();
    }

    private synchronized void setMusicGain(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            if (!volumeWarningShown) {
                errorLogger.accept("Audio output does not support menu music volume control.");
                volumeWarningShown = true;
            }
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = volume <= 0f ? gain.getMinimum() : (float) (20 * Math.log10(volume));
        float target = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels));
        if (muted) {
            mutedLineGains.put(clip, target);
            gain.setValue(gain.getMinimum());
        } else {
            gain.setValue(target);
        }
    }

    private void setDialogueGain(Clip clip) {
        setClipGain(clip, 0.75f, "dialogue sound");
    }

    private void setClipGain(Clip clip, float volume, String soundName) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            errorLogger.accept("Audio output does not support " + soundName + " volume control.");
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = (float) (20 * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
    }

    private synchronized void applyMute(Line line) {
        try {
            BooleanControl mute = (BooleanControl) line.getControl(BooleanControl.Type.MUTE);
            mute.setValue(muted);
        } catch (IllegalArgumentException ignored) {
        }
        if (!line.isOpen() || !line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
        if (muted) {
            mutedLineGains.putIfAbsent(line, gain.getValue());
            gain.setValue(gain.getMinimum());
        } else {
            Float previousGain = mutedLineGains.remove(line);
            if (previousGain != null) gain.setValue(previousGain);
        }
    }

    private synchronized void forgetMutedGain(Line line) {
        mutedLineGains.remove(line);
    }
}
