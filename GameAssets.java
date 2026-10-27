import javax.swing.ImageIcon;
import java.io.File;
import java.net.URISyntaxException;

final class GameAssets {
    private GameAssets() {}

    static File resolveFile(String path) {
        File root = getProjectDirectory();
        File asset = new File(root, path);
        return asset.isFile() ? asset : null;
    }

    private static File getProjectDirectory() {
        try {
            File classes = new File(GameAssets.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (classes.isDirectory()) {
                if (new File(classes, "audio").isDirectory()
                    || new File(classes, "images").isDirectory()) return classes;
                File parent = classes.getParentFile();
                if (parent != null && (new File(parent, "audio").isDirectory()
                    || new File(parent, "images").isDirectory())) return parent;
            } else {
                File parent = classes.getParentFile();
                if (parent != null) return parent;
            }
        } catch (URISyntaxException | SecurityException e) {
            System.err.println("Could not locate game folder: " + e.getMessage());
        }
        return new File(System.getProperty("user.dir"));
    }

    static ImageIcon loadImage(String path) {
        File file = resolveFile(path);
        if (file == null) {
            System.err.println("Image file not found in game folder: " + path);
            return new ImageIcon();
        }
        return new ImageIcon(file.getAbsolutePath());
    }
}
