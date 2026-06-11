package com.legendaryweaponssmp.hud;

import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

final class CompositeHudFrames {
    static final int RITUAL_SAMPLE_SECONDS = 2;
    static final int RITUAL_TIMER_SAMPLES = (600 / RITUAL_SAMPLE_SECONDS) + 1;
    static final int RITUAL_PROGRESS_STEPS = 101;
    static final int COOLDOWN_STEPS = 11;

    static final int RITUAL_CRAFTING_OFFSET = 0;
    static final int RITUAL_RUNNING_OFFSET = RITUAL_CRAFTING_OFFSET + RITUAL_PROGRESS_STEPS;
    static final int RITUAL_PAUSED_OFFSET = RITUAL_RUNNING_OFFSET + RITUAL_TIMER_SAMPLES;
    static final int RITUAL_FRAME_COUNT = RITUAL_PAUSED_OFFSET + RITUAL_TIMER_SAMPLES;

    static final int COOLDOWN_STATE_OFFSET = 0;
    static final int COOLDOWN_FRAME_COUNT = COOLDOWN_STATE_OFFSET + (COOLDOWN_STEPS * COOLDOWN_STEPS);

    static final int RITUAL_WIDTH = 630;
    static final int RITUAL_HEIGHT = 224;
    static final int COOLDOWN_WIDTH = 660;
    static final int COOLDOWN_HEIGHT = 170;

    static final int RITUAL_ATLAS_COLUMNS = 8;
    static final int RITUAL_ATLAS_ROWS = 8;
    static final int RITUAL_ATLAS_CAPACITY = RITUAL_ATLAS_COLUMNS * RITUAL_ATLAS_ROWS;
    static final int COOLDOWN_ATLAS_COLUMNS = 8;
    static final int COOLDOWN_ATLAS_ROWS = 8;
    static final int COOLDOWN_ATLAS_CAPACITY = COOLDOWN_ATLAS_COLUMNS * COOLDOWN_ATLAS_ROWS;

    private static final double RITUAL_SCALE_X = RITUAL_WIDTH / 370.0;
    private static final double RITUAL_SCALE_Y = RITUAL_HEIGHT / 132.0;
    private static final double COOLDOWN_SCALE_X = COOLDOWN_WIDTH / 304.0;
    private static final double COOLDOWN_SCALE_Y = COOLDOWN_HEIGHT / 78.0;

    private final JavaPlugin plugin;
    private final Font titleFont;
    private final Font infoFont;
    private final Font stageFont;
    private final Font cooldownFont;

    CompositeHudFrames(JavaPlugin plugin) {
        this.plugin = plugin;
        this.titleFont = loadFont(Font.BOLD, (float) (14f * RITUAL_SCALE_Y));
        this.infoFont = loadFont(Font.BOLD, (float) (10f * RITUAL_SCALE_Y));
        this.stageFont = loadFont(Font.BOLD, (float) (11f * RITUAL_SCALE_Y));
        this.cooldownFont = loadFont(Font.BOLD, (float) (8f * COOLDOWN_SCALE_Y));
    }

    void generateRitualAtlases(File dir,
                               File purpleBarFile,
                               String coordinates,
                               String title) throws IOException {
        BufferedImage sourceFrame = readResource("hud/ritual_frame.png");
        BufferedImage timerIcon = readResource("hud/ritual_timer_icon.png");
        BufferedImage purpleBar = ImageIO.read(purpleBarFile);
        if (sourceFrame == null || timerIcon == null || purpleBar == null) {
            throw new IOException("Ritual atlas source images are missing.");
        }

        try {
            for (int atlasIndex = 0; atlasIndex < ritualAtlasCount(); atlasIndex++) {
                BufferedImage atlas = new BufferedImage(
                    RITUAL_WIDTH * RITUAL_ATLAS_COLUMNS,
                    RITUAL_HEIGHT * RITUAL_ATLAS_ROWS,
                    BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D atlasGraphics = atlas.createGraphics();
                tune(atlasGraphics);
                try {
                    int firstFrame = atlasIndex * RITUAL_ATLAS_CAPACITY;
                    for (int cell = 0; cell < RITUAL_ATLAS_CAPACITY; cell++) {
                        int frameIndex = firstFrame + cell;
                        if (frameIndex >= RITUAL_FRAME_COUNT) {
                            break;
                        }
                        BufferedImage frame = renderRitualFrame(
                            sourceFrame,
                            timerIcon,
                            purpleBar,
                            coordinates,
                            title,
                            frameIndex
                        );
                        int x = (cell % RITUAL_ATLAS_COLUMNS) * RITUAL_WIDTH;
                        int y = (cell / RITUAL_ATLAS_COLUMNS) * RITUAL_HEIGHT;
                        atlasGraphics.drawImage(frame, x, y, null);
                        frame.flush();
                    }
                } finally {
                    atlasGraphics.dispose();
                }
                writePng(atlas, new File(dir, ritualAtlasFileName(atlasIndex)));
            }
        } finally {
            sourceFrame.flush();
            timerIcon.flush();
            purpleBar.flush();
        }
    }

    void generateCooldownAtlases(File dir, File purpleBarFile) throws IOException {
        BufferedImage sourceFrame = readResource("hud/cooldown_frame.png");
        BufferedImage purpleBar = ImageIO.read(purpleBarFile);
        if (sourceFrame == null || purpleBar == null) {
            throw new IOException("Cooldown atlas source images are missing.");
        }

        try {
            for (int atlasIndex = 0; atlasIndex < cooldownAtlasCount(); atlasIndex++) {
                BufferedImage atlas = new BufferedImage(
                    COOLDOWN_WIDTH * COOLDOWN_ATLAS_COLUMNS,
                    COOLDOWN_HEIGHT * COOLDOWN_ATLAS_ROWS,
                    BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D atlasGraphics = atlas.createGraphics();
                tune(atlasGraphics);
                try {
                    int firstFrame = atlasIndex * COOLDOWN_ATLAS_CAPACITY;
                    for (int cell = 0; cell < COOLDOWN_ATLAS_CAPACITY; cell++) {
                        int frameIndex = firstFrame + cell;
                        if (frameIndex >= COOLDOWN_FRAME_COUNT) {
                            break;
                        }
                        BufferedImage frame = renderCooldownFrame(sourceFrame, purpleBar, frameIndex);
                        int x = (cell % COOLDOWN_ATLAS_COLUMNS) * COOLDOWN_WIDTH;
                        int y = (cell / COOLDOWN_ATLAS_COLUMNS) * COOLDOWN_HEIGHT;
                        atlasGraphics.drawImage(frame, x, y, null);
                        frame.flush();
                    }
                } finally {
                    atlasGraphics.dispose();
                }
                writePng(atlas, new File(dir, cooldownAtlasFileName(atlasIndex)));
            }
        } finally {
            sourceFrame.flush();
            purpleBar.flush();
        }
    }

    static int ritualAtlasCount() {
        return divideRoundUp(RITUAL_FRAME_COUNT, RITUAL_ATLAS_CAPACITY);
    }

    static int cooldownAtlasCount() {
        return divideRoundUp(COOLDOWN_FRAME_COUNT, COOLDOWN_ATLAS_CAPACITY);
    }

    static String ritualAtlasFileName(int index) {
        return String.format(Locale.ROOT, "ritual_atlas_%02d.png", index);
    }

    static String cooldownAtlasFileName(int index) {
        return String.format(Locale.ROOT, "cooldown_atlas_%02d.png", index);
    }

    static int ritualCraftingFrame(int percent) {
        return RITUAL_CRAFTING_OFFSET + clamp(percent, 0, 100);
    }

    static int ritualRunningFrame(int remainingSeconds) {
        return RITUAL_RUNNING_OFFSET + sampledTimerIndex(remainingSeconds);
    }

    static int ritualPausedFrame(int remainingSeconds) {
        return RITUAL_PAUSED_OFFSET + sampledTimerIndex(remainingSeconds);
    }

    static int cooldownFrame(int leftStep, int rightStep) {
        return COOLDOWN_STATE_OFFSET
            + (clamp(leftStep, 0, 10) * COOLDOWN_STEPS)
            + clamp(rightStep, 0, 10);
    }

    private BufferedImage renderRitualFrame(BufferedImage sourceFrame,
                                            BufferedImage timerIcon,
                                            BufferedImage purpleBar,
                                            String coordinates,
                                            String title,
                                            int frameIndex) {
        if (frameIndex < RITUAL_RUNNING_OFFSET) {
            int percent = frameIndex - RITUAL_CRAFTING_OFFSET;
            return renderRitualState(
                sourceFrame,
                timerIcon,
                purpleBar,
                coordinates,
                title,
                0,
                percent,
                "CRAFTING WEAPON " + percent + "%",
                false
            );
        }
        if (frameIndex < RITUAL_PAUSED_OFFSET) {
            int remaining = (frameIndex - RITUAL_RUNNING_OFFSET) * RITUAL_SAMPLE_SECONDS;
            int progress = clamp((int) Math.round((600 - remaining) / 6.0), 0, 100);
            return renderRitualState(
                sourceFrame,
                timerIcon,
                purpleBar,
                coordinates,
                title,
                remaining,
                progress,
                null,
                false
            );
        }

        int remaining = (frameIndex - RITUAL_PAUSED_OFFSET) * RITUAL_SAMPLE_SECONDS;
        int progress = clamp((int) Math.round((600 - remaining) / 6.0), 0, 100);
        return renderRitualState(
            sourceFrame,
            timerIcon,
            purpleBar,
            coordinates,
            title,
            remaining,
            progress,
            "RITUAL PAUSED - RETURN TO AREA",
            true
        );
    }

    private BufferedImage renderRitualState(BufferedImage sourceFrame,
                                            BufferedImage timerIcon,
                                            BufferedImage purpleBar,
                                            String coordinates,
                                            String title,
                                            int remainingSeconds,
                                            int progress,
                                            String status,
                                            boolean paused) {
        BufferedImage image = new BufferedImage(RITUAL_WIDTH, RITUAL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        tune(graphics);
        graphics.drawImage(sourceFrame, 0, 0, RITUAL_WIDTH, RITUAL_HEIGHT, null);
        drawCentered(
            graphics,
            title == null || title.isBlank() ? "LEGENDARY WEAPONS RITUAL" : title,
            titleFont,
            new Color(248, 244, 255),
            RITUAL_WIDTH / 2,
            ry(41)
        );
        drawLeft(
            graphics,
            coordinates == null || coordinates.isBlank() ? "X 0  Y 0  Z 0" : coordinates,
            infoFont,
            new Color(255, 224, 113),
            rx(60),
            ry(55)
        );
        graphics.drawImage(timerIcon, rx(270), ry(47), rx(10), ry(10), null);
        drawLeft(
            graphics,
            formatTime(remainingSeconds),
            infoFont,
            new Color(122, 248, 255),
            rx(283),
            ry(55)
        );
        drawProgressTexture(
            graphics,
            purpleBar,
            rx(65),
            ry(72),
            rx(241),
            ry(18),
            progress,
            100,
            paused
        );
        if (status != null) {
            drawCentered(
                graphics,
                status,
                stageFont,
                new Color(255, 91, 104),
                RITUAL_WIDTH / 2,
                ry(87)
            );
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage renderCooldownFrame(BufferedImage sourceFrame,
                                              BufferedImage purpleBar,
                                              int frameIndex) {
        int stateIndex = frameIndex - COOLDOWN_STATE_OFFSET;
        int leftStep = stateIndex / COOLDOWN_STEPS;
        int rightStep = stateIndex % COOLDOWN_STEPS;
        return renderCooldownState(sourceFrame, purpleBar, leftStep, rightStep);
    }

    private BufferedImage renderCooldownState(BufferedImage sourceFrame,
                                              BufferedImage purpleBar,
                                              int leftStep,
                                              int rightStep) {
        BufferedImage image = new BufferedImage(COOLDOWN_WIDTH, COOLDOWN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        tune(graphics);
        graphics.drawImage(sourceFrame, 0, 0, COOLDOWN_WIDTH, COOLDOWN_HEIGHT, null);
        drawCooldownSlot(graphics, purpleBar, true, leftStep);
        drawCooldownSlot(graphics, purpleBar, false, rightStep);
        graphics.dispose();
        return image;
    }

    private void drawCooldownSlot(Graphics2D graphics,
                                  BufferedImage purpleBar,
                                  boolean left,
                                  int step) {
        if (left) {
            drawProgressTexture(graphics, purpleBar, cx(54), cy(34), cx(88), cy(15), step, 10, false);
            drawCentered(
                graphics,
                step >= 10 ? "READY" : (step * 10) + "%",
                cooldownFont,
                new Color(216, 154, 255),
                cx(98),
                cy(46)
            );
        } else {
            drawProgressTexture(graphics, purpleBar, cx(163), cy(34), cx(92), cy(15), step, 10, false);
            drawCentered(
                graphics,
                step >= 10 ? "READY" : (step * 10) + "%",
                cooldownFont,
                new Color(216, 154, 255),
                cx(209),
                cy(46)
            );
        }
    }

    private void drawProgressTexture(Graphics2D graphics,
                                     BufferedImage texture,
                                     int x,
                                     int y,
                                     int width,
                                     int height,
                                     int units,
                                     int maximumUnits,
                                     boolean dimmed) {
        double ratio = units / (double) maximumUnits;
        int fillWidth = Math.max(0, Math.min(width, (int) Math.round(width * ratio)));
        if (fillWidth <= 0) {
            return;
        }
        Composite previous = graphics.getComposite();
        if (dimmed) {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.62f));
        }
        graphics.setClip(x, y, fillWidth, height);
        graphics.drawImage(texture, x, y, width, height, null);
        graphics.setClip(null);
        graphics.setComposite(previous);
    }

    private void drawCentered(Graphics2D graphics,
                              String text,
                              Font font,
                              Color color,
                              int centerX,
                              int baselineY) {
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        drawText(graphics, text, color, centerX - (metrics.stringWidth(text) / 2), baselineY);
    }

    private void drawLeft(Graphics2D graphics,
                          String text,
                          Font font,
                          Color color,
                          int x,
                          int baselineY) {
        graphics.setFont(font);
        drawText(graphics, text, color, x, baselineY);
    }

    private void drawText(Graphics2D graphics, String text, Color color, int x, int y) {
        graphics.setColor(new Color(8, 4, 16, 225));
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    private BufferedImage readResource(String path) throws IOException {
        try (InputStream input = plugin.getResource(path)) {
            return input == null ? null : ImageIO.read(input);
        }
    }

    private void writePng(BufferedImage image, File file) throws IOException {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", file);
        image.flush();
    }

    private Font loadFont(int style, float size) {
        for (String path : new String[]{
            "C:\\Windows\\Fonts\\bahnschrift.ttf",
            "C:\\Windows\\Fonts\\consolab.ttf"
        }) {
            File file = new File(path);
            if (!file.isFile()) {
                continue;
            }
            try {
                return Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(style, size);
            } catch (Exception ignored) {
                // Fall through to the platform font.
            }
        }
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }

    private String formatTime(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60);
    }

    private void tune(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private int rx(int value) {
        return (int) Math.round(value * RITUAL_SCALE_X);
    }

    private int ry(int value) {
        return (int) Math.round(value * RITUAL_SCALE_Y);
    }

    private int cx(int value) {
        return (int) Math.round(value * COOLDOWN_SCALE_X);
    }

    private int cy(int value) {
        return (int) Math.round(value * COOLDOWN_SCALE_Y);
    }

    private static int sampledTimerIndex(int remainingSeconds) {
        int clamped = clamp(remainingSeconds, 0, 600);
        return clamp((int) Math.round(clamped / (double) RITUAL_SAMPLE_SECONDS), 0, RITUAL_TIMER_SAMPLES - 1);
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
