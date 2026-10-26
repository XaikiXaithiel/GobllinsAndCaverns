import javax.sound.sampled.*;
import java.io.File;
import java.util.function.Consumer;

final class GameAudio {
    private final Consumer<String> errorLogger;
    private SourceDataLine narrationLine;
    private Thread narrationThread;
    private long narrationStartedAt;
    private volatile Clip dialogueBoopClip;
    private volatile boolean dialogueBoopRequested;
    private volatile Clip homeMusicClip;
    private volatile long homeMusicFadeStartedAt;
    private volatile float homeMusicVolume = 0.7f;
    private volatile boolean muted;
    private boolean volumeWarningShown;
    private long menuMusicRequest;
    private boolean menuMusicLoading;

    GameAudio(Consumer<String> errorLogger) {
        this.errorLogger = errorLogger;
        loadDialogueBoopAsync();
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
            narrationStartedAt = System.currentTimeMillis();
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
        if (homeMusicClip != null && homeMusicClip.isOpen()) applyMute(homeMusicClip);
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

    synchronized void playDialogueBoop() {
        Clip clip = dialogueBoopClip;
        if (clip == null || !clip.isOpen()) {
            dialogueBoopRequested = true;
            return;
        }
        startDialogueBoop(clip);
    }

    private void startDialogueBoop(Clip clip) {
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    boolean blocksAdvanceForNarration() {
        if (narrationLine == null || !narrationLine.isRunning()) return false;
        if (System.currentTimeMillis() - narrationStartedAt >= 500) stopNarration();
        return true;
    }

    void pauseNarration() {
        if (narrationLine != null && narrationLine.isRunning()) narrationLine.stop();
    }

    void resumeNarration(long pausedFor) {
        if (narrationLine != null && narrationLine.isOpen()) {
            narrationStartedAt += pausedFor;
            narrationLine.start();
        }
    }

    void stopNarration() {
        SourceDataLine line = narrationLine;
        narrationLine = null;
        if (line != null) {
            line.stop();
            line.close();
        }
        Thread thread = narrationThread;
        narrationThread = null;
        if (thread != null) thread.interrupt();
    }

    private void setMusicGain(Clip clip, float volume) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            if (!volumeWarningShown) {
                errorLogger.accept("Audio output does not support menu music volume control.");
                volumeWarningShown = true;
            }
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float decibels = volume <= 0f ? gain.getMinimum() : (float) (20 * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
    }

    private void applyMute(Line line) {
        try {
            BooleanControl mute = (BooleanControl) line.getControl(BooleanControl.Type.MUTE);
            mute.setValue(muted);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
