package org.aouessar.app;

import org.aouessar.core.gen.impl.DefaultBiomeGenerator;
import org.aouessar.core.gen.impl.SimpleWorldGenerator;
import org.aouessar.core.world.layers.BiomeMap;
import org.aouessar.core.world.layers.Heightmap;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 2D Biome Map Viewer - displays a top-down view of the world's biomes.
 * Features:
 * - Smooth pan with mouse drag (tile-based progressive rendering)
 * - Zoom with mouse wheel
 * - Hover to see biome name and coordinates
 * - Color-coded biomes matching the 3D world
 * - Chunks render progressively as you explore
 */
public class BiomeMapViewer extends JFrame {

    // World generation
    private final long seed;
    private final SimpleWorldGenerator worldGen = new SimpleWorldGenerator();
    private final DefaultBiomeGenerator biomeGen = new DefaultBiomeGenerator();

    // Region cache - each region is REGION_SIZE x REGION_SIZE blocks (much more efficient than small tiles)
    // Using 256x256 block regions (same as Minecraft region size) reduces generation overhead significantly
    private static final int REGION_SIZE = 256; // blocks per region (16x16 chunks)
    private final Map<Long, BufferedImage> regionCache = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> regionsInProgress = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 256; // Maximum regions to cache (256 regions for smooth panning)

    // Track which regions are currently visible to avoid unnecessary eviction
    private final Set<Long> currentlyVisibleRegions = ConcurrentHashMap.newKeySet();

    // Flag to track if new regions were loaded (only repaint when necessary)
    private volatile boolean needsRepaint = false;

    // View state
    private double centerX = 0;
    private double centerZ = 0;
    private double zoom = 1.0; // pixels per block
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 8.0;

    // Mouse tracking
    private int lastMouseX, lastMouseY;
    private int hoverWorldX, hoverWorldZ;
    private short hoverBiomeId = -1;
    private int hoverHeight = 0;
    private boolean isDragging = false;

    // Async tile generation
    private final ExecutorService tileExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
    );

    // Render timer for smooth updates
    private final Timer renderTimer;

    // Biome colors (matching typical Minecraft biome colors)
    private static final Color[] BIOME_COLORS = new Color[16];
    private static final String[] BIOME_NAMES = new String[16];
    private static final Color WATER_COLOR = new Color(30, 60, 180);
    private static final Color LOADING_COLOR = new Color(60, 60, 60);
    private static final Color BEACH_COLOR = new Color(250, 222, 85);  // Sandy yellow
    private static final String BEACH_NAME = "Beach";

    // Special biome ID for beach (display only, not a real biome)
    private static final short DISPLAY_BEACH = 100;

    static {
        // Initialize biome colors and names
        BIOME_COLORS[EngineConfig.BIOME_PLAINS] = new Color(141, 179, 96);    // Light green
        BIOME_COLORS[EngineConfig.BIOME_DESERT] = new Color(250, 148, 24);    // Orange/tan
        BIOME_COLORS[EngineConfig.BIOME_SNOW] = new Color(255, 255, 255);     // White
        BIOME_COLORS[EngineConfig.BIOME_FOREST] = new Color(5, 102, 33);      // Dark green
        BIOME_COLORS[EngineConfig.BIOME_SAVANNA] = new Color(189, 178, 95);   // Tan/yellow
        BIOME_COLORS[EngineConfig.BIOME_SWAMP] = new Color(47, 112, 91);      // Murky green
        BIOME_COLORS[EngineConfig.BIOME_JUNGLE] = new Color(99, 55, 47);      // Brown

        BIOME_NAMES[EngineConfig.BIOME_PLAINS] = "Plains";
        BIOME_NAMES[EngineConfig.BIOME_DESERT] = "Desert";
        BIOME_NAMES[EngineConfig.BIOME_SNOW] = "Snow";
        BIOME_NAMES[EngineConfig.BIOME_FOREST] = "Forest";
        BIOME_NAMES[EngineConfig.BIOME_SAVANNA] = "Savanna";
        BIOME_NAMES[EngineConfig.BIOME_SWAMP] = "Swamp";
        BIOME_NAMES[EngineConfig.BIOME_JUNGLE] = "Jungle";

        // Fill remaining with defaults
        for (int i = 0; i < BIOME_COLORS.length; i++) {
            if (BIOME_COLORS[i] == null) BIOME_COLORS[i] = Color.GRAY;
            if (BIOME_NAMES[i] == null) BIOME_NAMES[i] = "Unknown";
        }
    }

    private final JPanel mapPanel;

    public BiomeMapViewer(long seed) {
        super("Biome Map Viewer - Seed: " + seed);
        this.seed = seed;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 720);
        setLocationRelativeTo(null);

        mapPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawMap((Graphics2D) g);
            }
        };
        mapPanel.setBackground(WATER_COLOR);
        mapPanel.setDoubleBuffered(true);

        // Mouse listeners for panning
        mapPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isDragging = true;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    mapPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isDragging = false;
                    mapPanel.setCursor(Cursor.getDefaultCursor());
                }
            }
        });

        mapPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging) {
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();

                    // Pan the view (convert screen pixels to world blocks)
                    centerX -= dx / zoom;
                    centerZ -= dy / zoom;

                    // Immediate repaint for smooth dragging
                    mapPanel.repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHoverInfo(e.getX(), e.getY());
                // Only repaint the small hover overlay area, not the entire panel
                mapPanel.repaint(0, 0, 250, 60);  // Hover info area
            }
        });

        // Mouse wheel for zooming
        mapPanel.addMouseWheelListener(e -> {
            double oldZoom = zoom;
            int rotation = e.getWheelRotation();

            if (rotation < 0) {
                zoom = Math.min(MAX_ZOOM, zoom * 1.2);
            } else {
                zoom = Math.max(MIN_ZOOM, zoom / 1.2);
            }

            if (zoom != oldZoom) {
                // Zoom toward mouse position
                int mx = e.getX();
                int my = e.getY();
                int pw = mapPanel.getWidth();
                int ph = mapPanel.getHeight();

                double worldXBefore = centerX + (mx - pw / 2.0) / oldZoom;
                double worldZBefore = centerZ + (my - ph / 2.0) / oldZoom;
                double worldXAfter = centerX + (mx - pw / 2.0) / zoom;
                double worldZAfter = centerZ + (my - ph / 2.0) / zoom;

                centerX += worldXBefore - worldXAfter;
                centerZ += worldZBefore - worldZAfter;

                mapPanel.repaint();
            }
        });

        // Keyboard shortcuts
        mapPanel.setFocusable(true);
        mapPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int moveAmount = (int) (100 / zoom);
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_A -> centerX -= moveAmount;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> centerX += moveAmount;
                    case KeyEvent.VK_UP, KeyEvent.VK_W -> centerZ -= moveAmount;
                    case KeyEvent.VK_DOWN, KeyEvent.VK_S -> centerZ += moveAmount;
                    case KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS -> zoom = Math.min(MAX_ZOOM, zoom * 1.2);
                    case KeyEvent.VK_MINUS -> zoom = Math.max(MIN_ZOOM, zoom / 1.2);
                    case KeyEvent.VK_HOME -> {
                        centerX = 0;
                        centerZ = 0;
                        zoom = 1.0;
                    }
                    case KeyEvent.VK_C -> {
                        // Clear cache
                        regionCache.clear();
                        regionsInProgress.clear();
                    }
                }
                mapPanel.repaint();
            }
        });

        add(mapPanel, BorderLayout.CENTER);

        // Info panel at bottom
        JPanel infoPanel = createInfoPanel();
        add(infoPanel, BorderLayout.SOUTH);

        // Timer for periodic repaint only when new tiles have loaded
        renderTimer = new Timer(100, e -> {
            if (needsRepaint) {
                needsRepaint = false;
                mapPanel.repaint();
            }
        }); // Check every 100ms if new regions loaded
        renderTimer.start();

        // Request initial focus
        SwingUtilities.invokeLater(mapPanel::requestFocusInWindow);
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        panel.setBackground(new Color(40, 40, 40));
        panel.setPreferredSize(new Dimension(0, 30));

        // Create legend for biomes
        for (int i = 0; i < 7; i++) {
            addLegendItem(panel, BIOME_COLORS[i], BIOME_NAMES[i]);
        }

        // Add Beach to the legend
        addLegendItem(panel, BEACH_COLOR, BEACH_NAME);

        return panel;
    }

    private void addLegendItem(JPanel panel, Color color, String name) {
        JPanel colorBox = new JPanel();
        colorBox.setBackground(color);
        colorBox.setPreferredSize(new Dimension(14, 14));
        colorBox.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        JLabel label = new JLabel(name);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));

        panel.add(colorBox);
        panel.add(label);
        panel.add(Box.createHorizontalStrut(5));
    }

    private void updateHoverInfo(int screenX, int screenY) {
        int pw = mapPanel.getWidth();
        int ph = mapPanel.getHeight();

        hoverWorldX = (int) (centerX + (screenX - pw / 2.0) / zoom);
        hoverWorldZ = (int) (centerZ + (screenY - ph / 2.0) / zoom);

        // Generate biome info on-demand for hover location (single block, very fast)
        LayerRect hoverRect = new LayerRect(hoverWorldX, hoverWorldZ, 1, 1);
        Heightmap hm = worldGen.generateHeightmap(seed, hoverRect);
        BiomeMap bm = biomeGen.generateBiomes(seed, hm);
        hoverBiomeId = bm.biomeIdAt(hoverWorldX, hoverWorldZ);
        hoverHeight = hm.heightAt(hoverWorldX, hoverWorldZ);

        // Check if this is a beach area - EXACT same logic as ChunkBuilder.java
        int seaLevel = EngineConfig.SEA_LEVEL;
        int beachBand = EngineConfig.BEACH_BAND;
        boolean isBeach = Math.abs(hoverHeight - seaLevel) <= beachBand &&
                          hoverHeight >= seaLevel - 2 && hoverHeight <= seaLevel + 6;
        boolean canBeBeach = hoverBiomeId != EngineConfig.BIOME_SNOW &&
                             hoverBiomeId != EngineConfig.BIOME_DESERT;

        if (isBeach && canBeBeach) {
            hoverBiomeId = DISPLAY_BEACH;  // Special ID for beach display
        }
    }

    private void drawMap(Graphics2D g) {
        int pw = mapPanel.getWidth();
        int ph = mapPanel.getHeight();

        if (pw <= 0 || ph <= 0) return;

        // Enable anti-aliasing for smoother rendering
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Fill background (ocean)
        g.setColor(WATER_COLOR);
        g.fillRect(0, 0, pw, ph);

        // Calculate visible region range
        double regionPixelSize = REGION_SIZE * zoom;

        int viewMinX = (int) Math.floor((centerX - pw / (2.0 * zoom)) / REGION_SIZE);
        int viewMaxX = (int) Math.ceil((centerX + pw / (2.0 * zoom)) / REGION_SIZE);
        int viewMinZ = (int) Math.floor((centerZ - ph / (2.0 * zoom)) / REGION_SIZE);
        int viewMaxZ = (int) Math.ceil((centerZ + ph / (2.0 * zoom)) / REGION_SIZE);

        // Clear and update currently visible regions set
        currentlyVisibleRegions.clear();
        for (int rz = viewMinZ; rz <= viewMaxZ; rz++) {
            for (int rx = viewMinX; rx <= viewMaxX; rx++) {
                currentlyVisibleRegions.add(getRegionKey(rx, rz));
            }
        }

        // Draw visible regions
        for (int rz = viewMinZ; rz <= viewMaxZ; rz++) {
            for (int rx = viewMinX; rx <= viewMaxX; rx++) {
                drawRegion(g, rx, rz, pw, ph, regionPixelSize);
            }
        }

        // Draw crosshair at center
        g.setColor(new Color(255, 255, 255, 100));
        g.drawLine(pw / 2 - 10, ph / 2, pw / 2 + 10, ph / 2);
        g.drawLine(pw / 2, ph / 2 - 10, pw / 2, ph / 2 + 10);

        // Draw hover info overlay
        drawHoverOverlay(g);

        // Draw coordinates and zoom info
        drawInfoOverlay(g, pw, ph);
    }

    private void drawRegion(Graphics2D g, int regionX, int regionZ, int panelWidth, int panelHeight, double regionPixelSize) {
        long regionKey = getRegionKey(regionX, regionZ);

        // Calculate screen position for this region
        double worldRegionX = regionX * REGION_SIZE;
        double worldRegionZ = regionZ * REGION_SIZE;

        int screenX = (int) ((worldRegionX - centerX) * zoom + panelWidth / 2.0);
        int screenZ = (int) ((worldRegionZ - centerZ) * zoom + panelHeight / 2.0);
        int screenWidth = (int) Math.ceil(regionPixelSize);
        int screenHeight = (int) Math.ceil(regionPixelSize);

        // Check if region is in cache
        BufferedImage regionImage = regionCache.get(regionKey);

        if (regionImage != null) {
            // Draw cached region
            g.drawImage(regionImage, screenX, screenZ, screenWidth, screenHeight, null);
        } else {
            // Draw placeholder and request generation
            g.setColor(LOADING_COLOR);
            g.fillRect(screenX, screenZ, screenWidth, screenHeight);

            // Draw loading indicator with region coordinates
            g.setColor(new Color(80, 80, 80));
            g.drawRect(screenX, screenZ, screenWidth - 1, screenHeight - 1);

            // Show "Loading..." text if region is large enough on screen
            if (screenWidth > 60) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(new Color(100, 100, 100));
                String loadingText = "Loading...";
                g.drawString(loadingText, screenX + 5, screenZ + 15);
            }

            // Request region generation if not already in progress
            requestRegionGeneration(regionX, regionZ, regionKey);
        }
    }

    private void requestRegionGeneration(int regionX, int regionZ, long regionKey) {
        // Check if already generating this region
        if (regionsInProgress.putIfAbsent(regionKey, Boolean.TRUE) != null) {
            return; // Already in progress
        }

        // Limit cache size
        if (regionCache.size() > MAX_CACHE_SIZE) {
            // Simple eviction: remove regions furthest from view center
            evictDistantRegions();
        }

        // Submit region generation task
        tileExecutor.submit(() -> {
            try {
                BufferedImage region = generateRegion(regionX, regionZ);
                regionCache.put(regionKey, region);
                needsRepaint = true;  // Signal that we need a repaint
            } catch (Exception e) {
                System.err.println("Error generating region (" + regionX + ", " + regionZ + "): " + e.getMessage());
            } finally {
                regionsInProgress.remove(regionKey);
            }
        });
    }

    private void evictDistantRegions() {
        // Remove regions that are far from current view, but NEVER remove currently visible ones
        int currentRegionX = (int) Math.floor(centerX / REGION_SIZE);
        int currentRegionZ = (int) Math.floor(centerZ / REGION_SIZE);
        int maxDistance = 12; // Regions beyond this distance can be evicted

        regionCache.entrySet().removeIf(entry -> {
            long key = entry.getKey();
            // Never evict currently visible regions
            if (currentlyVisibleRegions.contains(key)) {
                return false;
            }
            int rx = (int) (key >> 32);
            int rz = (int) key;
            int dist = Math.abs(rx - currentRegionX) + Math.abs(rz - currentRegionZ);
            return dist > maxDistance;
        });
    }

    private BufferedImage generateRegion(int regionX, int regionZ) {
        int worldMinX = regionX * REGION_SIZE;
        int worldMinZ = regionZ * REGION_SIZE;

        // Generate heightmap and biome map for this entire region at once
        // This is MUCH more efficient than generating many small tiles
        LayerRect rect = new LayerRect(worldMinX, worldMinZ, REGION_SIZE, REGION_SIZE);
        Heightmap heightmap = worldGen.generateHeightmap(seed, rect);
        BiomeMap biomeMap = biomeGen.generateBiomes(seed, heightmap);

        int seaLevel = EngineConfig.SEA_LEVEL;
        int beachBand = EngineConfig.BEACH_BAND;

        // Create region image
        BufferedImage image = new BufferedImage(REGION_SIZE, REGION_SIZE, BufferedImage.TYPE_INT_RGB);

        // Use direct pixel array access for faster rendering
        int[] pixels = new int[REGION_SIZE * REGION_SIZE];

        // Render each pixel
        for (int lz = 0; lz < REGION_SIZE; lz++) {
            for (int lx = 0; lx < REGION_SIZE; lx++) {
                int worldX = worldMinX + lx;
                int worldZ = worldMinZ + lz;

                short biomeId = biomeMap.biomeIdAt(worldX, worldZ);
                int h = heightmap.heightAt(worldX, worldZ);

                int rgb;
                if (h < seaLevel) {
                    // Water - use depth-based blue
                    int depth = seaLevel - h;
                    int blue = Math.max(80, 200 - depth * 3);
                    int green = Math.max(40, 100 - depth * 2);
                    rgb = (30 << 16) | (green << 8) | blue;
                } else {
                    // Check for beach - EXACT same logic as ChunkBuilder.java
                    // ChunkBuilder: Math.abs(surfaceY - sea) <= BEACH_BAND && surfaceY >= sea - 2 && surfaceY <= sea + 6
                    boolean isBeach = Math.abs(h - seaLevel) <= beachBand && h >= seaLevel - 2 && h <= seaLevel + 6;
                    boolean canBeBeach = biomeId != EngineConfig.BIOME_SNOW &&
                                         biomeId != EngineConfig.BIOME_DESERT;

                    Color baseColor;
                    if (isBeach && canBeBeach) {
                        // Beach area
                        float beachBrightness = 0.95f + (h - seaLevel) * 0.01f;
                        int r = Math.min(255, (int) (BEACH_COLOR.getRed() * beachBrightness));
                        int g = Math.min(255, (int) (BEACH_COLOR.getGreen() * beachBrightness));
                        int b = Math.min(255, (int) (BEACH_COLOR.getBlue() * beachBrightness));
                        rgb = (r << 16) | (g << 8) | b;
                    } else {
                        // Land - use biome color with height shading
                        if (biomeId >= 0 && biomeId < BIOME_COLORS.length) {
                            baseColor = BIOME_COLORS[biomeId];
                        } else {
                            baseColor = Color.GRAY;
                        }
                        baseColor = applyHeightShading(baseColor, h, seaLevel);
                        rgb = baseColor.getRGB();
                    }
                }

                pixels[lz * REGION_SIZE + lx] = rgb;
            }
        }

        // Set all pixels at once (faster than setRGB for each pixel)
        image.setRGB(0, 0, REGION_SIZE, REGION_SIZE, pixels, 0, REGION_SIZE);

        // Add contour lines
        addContourLines(image, heightmap, seaLevel);

        return image;
    }

    private long getRegionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }

    private Color applyHeightShading(Color base, int height, int seaLevel) {
        float heightFactor = Math.min(1.0f, (height - seaLevel) / 150.0f);
        float brightness = 0.85f + heightFactor * 0.3f;

        // Add snow on very high areas
        if (height > seaLevel + 100) {
            float snowBlend = Math.min(1.0f, (height - seaLevel - 100) / 80.0f);
            int r = (int) ((base.getRed() * (1 - snowBlend) + 255 * snowBlend) * brightness);
            int g = (int) ((base.getGreen() * (1 - snowBlend) + 255 * snowBlend) * brightness);
            int b = (int) ((base.getBlue() * (1 - snowBlend) + 255 * snowBlend) * brightness);
            return new Color(
                    Math.min(255, Math.max(0, r)),
                    Math.min(255, Math.max(0, g)),
                    Math.min(255, Math.max(0, b))
            );
        }

        int r = Math.min(255, Math.max(0, (int) (base.getRed() * brightness)));
        int g = Math.min(255, Math.max(0, (int) (base.getGreen() * brightness)));
        int b = Math.min(255, Math.max(0, (int) (base.getBlue() * brightness)));

        return new Color(r, g, b);
    }

    private void addContourLines(BufferedImage image, Heightmap heightmap, int seaLevel) {
        int contourInterval = 20;
        LayerRect rect = heightmap.rect();
        int size = rect.sizeX; // Should be REGION_SIZE

        for (int lz = 1; lz < size - 1; lz++) {
            for (int lx = 1; lx < size - 1; lx++) {
                int worldX = rect.minX + lx;
                int worldZ = rect.minZ + lz;

                int h = heightmap.heightAt(worldX, worldZ);
                if (h < seaLevel) continue;

                int hRight = heightmap.heightAt(worldX + 1, worldZ);
                int hDown = heightmap.heightAt(worldX, worldZ + 1);

                int level = (h - seaLevel) / contourInterval;
                int levelRight = (hRight - seaLevel) / contourInterval;
                int levelDown = (hDown - seaLevel) / contourInterval;

                if (level != levelRight || level != levelDown) {
                    int rgb = image.getRGB(lx, lz);
                    int r = Math.max(0, ((rgb >> 16) & 0xFF) - 25);
                    int g = Math.max(0, ((rgb >> 8) & 0xFF) - 25);
                    int b = Math.max(0, (rgb & 0xFF) - 25);
                    image.setRGB(lx, lz, (r << 16) | (g << 8) | b);
                }
            }
        }
    }

    private void drawHoverOverlay(Graphics2D g) {
        // Determine biome name and color for hover display
        String biomeName;
        Color biomeColor;

        if (hoverBiomeId == DISPLAY_BEACH) {
            biomeName = BEACH_NAME;
            biomeColor = BEACH_COLOR;
        } else if (hoverBiomeId >= 0 && hoverBiomeId < BIOME_NAMES.length) {
            biomeName = BIOME_NAMES[hoverBiomeId];
            biomeColor = BIOME_COLORS[hoverBiomeId];
        } else {
            return; // Unknown biome, don't display
        }

        String coords = String.format("X: %d  Z: %d  Y: %d", hoverWorldX, hoverWorldZ, hoverHeight);

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = Math.max(fm.stringWidth(biomeName), fm.stringWidth(coords));
        int boxWidth = textWidth + 30;
        int boxHeight = 50;
        int boxX = 10;
        int boxY = 10;

        // Semi-transparent background
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);

        // Biome color indicator
        g.setColor(biomeColor);
        g.fillRect(boxX + 10, boxY + 10, 12, 12);
        g.setColor(Color.BLACK);
        g.drawRect(boxX + 10, boxY + 10, 12, 12);

        // Biome name
        g.setColor(Color.WHITE);
        g.drawString(biomeName, boxX + 28, boxY + 21);

        // Coordinates
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(200, 200, 200));
        g.drawString(coords, boxX + 10, boxY + 40);
    }

    private void drawInfoOverlay(Graphics2D g, int pw, int ph) {
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));

        String zoomStr = String.format("Zoom: %.1fx", zoom);
        String centerStr = String.format("Center: (%d, %d)", (int) centerX, (int) centerZ);
        String seedStr = "Seed: " + seed;
        String cacheStr = String.format("Regions: %d cached", regionCache.size());

        int x = pw - 160;
        int y = 20;

        // Background
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(x - 10, y - 15, 165, 75, 8, 8);

        g.setColor(new Color(255, 255, 255, 200));
        g.drawString(seedStr, x, y);
        g.drawString(centerStr, x, y + 15);
        g.drawString(zoomStr, x, y + 30);
        g.drawString(cacheStr, x, y + 45);

        // Controls hint
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(new Color(180, 180, 180));
        String hint = "Drag to pan | Scroll to zoom | Home to reset | C to clear cache";
        g.drawString(hint, 10, ph - 40);
    }

    @Override
    public void dispose() {
        renderTimer.stop();
        tileExecutor.shutdownNow();
        super.dispose();
    }

    public static void main(String[] args) {
        // Use the same seed as Main class
        long seed = 905282311L;

        // Parse command line seed if provided
        if (args.length > 0) {
            try {
                seed = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid seed, using default: " + seed);
            }
        }

        final long finalSeed = seed;

        // Launch on EDT
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            BiomeMapViewer viewer = new BiomeMapViewer(finalSeed);
            viewer.setVisible(true);
        });
    }
}
