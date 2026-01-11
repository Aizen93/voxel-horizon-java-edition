package org.aouessar.renderer.ui;

import org.aouessar.core.api.BiomeLocator;
import org.aouessar.shared.EngineConfig;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class BiomeTeleportDialog {
    private BiomeTeleportDialog() {}

    public static Optional<TeleportTarget> prompt(int startX, int startZ, BiomeLocator locator) {
        AtomicReference<Optional<TeleportTarget>> out = new AtomicReference<>(Optional.empty());

        Runnable task = () -> out.set(showDialog(startX, startZ, locator));

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (Exception e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }

        return out.get();
    }

    private static Optional<TeleportTarget> showDialog(
            int startX, int startZ,
            BiomeLocator locator
    ) {
        final JDialog dialog = new JDialog((Frame) null, "Teleport to Biome", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setAlwaysOnTop(true);

        // Biome choices (IDs from EngineConfig)
        final JComboBox<BiomeChoice> biomeBox = getBiomeChoiceJComboBox();
        biomeBox.setSelectedIndex(0);

        final JTextField radiusField = new JTextField("200000", 10); // blocks
        final JLabel status = new JLabel(" ");
        final JLabel err = new JLabel(" ");
        err.setForeground(Color.RED);

        final JButton find = new JButton("Find & Teleport");
        final JButton cancel = new JButton("Cancel");

        final AtomicReference<Optional<TeleportTarget>> result = new AtomicReference<>(Optional.empty());

        find.addActionListener(ev -> {
            err.setText(" ");
            status.setText("Searching...");
            find.setEnabled(false);
            biomeBox.setEnabled(false);
            radiusField.setEnabled(false);

            final int maxRadiusFinal;
            try {
                int parsed = Integer.parseInt(radiusField.getText().trim().replace(",", ""));
                if (parsed < 1024) parsed = 1024;
                maxRadiusFinal = parsed;
            } catch (Exception ex) {
                status.setText(" ");
                err.setText("Radius must be a number (e.g. 50000)");
                find.setEnabled(true);
                biomeBox.setEnabled(true);
                radiusField.setEnabled(true);
                return;
            }

            final BiomeChoice choice = (BiomeChoice) biomeBox.getSelectedItem();
            if (choice == null) {
                status.setText(" ");
                err.setText("Select a biome.");
                find.setEnabled(true);
                biomeBox.setEnabled(true);
                radiusField.setEnabled(true);
                return;
            }

            final int targetBiomeFinal = choice.biomeId;
            final int startXFinal = startX;
            final int startZFinal = startZ;

            SwingWorker<Optional<TeleportTarget>, Void> worker = new SwingWorker<>() {
                @Override
                protected Optional<TeleportTarget> doInBackground() {
                    var hit = locator.findNearestBiome(startXFinal, startZFinal, targetBiomeFinal, maxRadiusFinal);
                    if (hit.isEmpty()) return Optional.empty();

                    int wx = hit.get().wx();
                    int wz = hit.get().wz();

                    int y = 250;


                    return Optional.of(new TeleportTarget(wx, y, wz));
                }

                @Override
                protected void done() {
                    try {
                        Optional<TeleportTarget> t = get();
                        if (t.isPresent()) {
                            result.set(t);
                            dialog.dispose();
                        } else {
                            status.setText(" ");
                            err.setText("Biome not found in that radius.");
                            find.setEnabled(true);
                            biomeBox.setEnabled(true);
                            radiusField.setEnabled(true);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        status.setText(" ");
                        err.setText("Search failed (see console).");
                        find.setEnabled(true);
                        biomeBox.setEnabled(true);
                        radiusField.setEnabled(true);
                    }
                }
            };

            worker.execute();
        });

        cancel.addActionListener(ev -> {
            result.set(Optional.empty());
            dialog.dispose();
        });

        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                result.set(Optional.empty());
            }
        });

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Biome:"));
        row1.add(biomeBox);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Max radius (blocks):"));
        row2.add(radiusField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(find);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(new JLabel("<html>Find the nearest biome from your current position.</html>"));
        root.add(Box.createVerticalStrut(8));
        root.add(row1);
        root.add(row2);
        root.add(Box.createVerticalStrut(6));
        root.add(status);
        root.add(err);
        root.add(Box.createVerticalStrut(8));
        root.add(buttons);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        SwingUtilities.invokeLater(radiusField::requestFocusInWindow);

        dialog.setVisible(true);
        dialog.dispose();

        return result.get();
    }

    @NotNull
    private static JComboBox<BiomeChoice> getBiomeChoiceJComboBox() {
        BiomeChoice[] choices = new BiomeChoice[] {
            new BiomeChoice("Plains",  EngineConfig.BIOME_PLAINS),
            new BiomeChoice("Forest",  EngineConfig.BIOME_FOREST),
            new BiomeChoice("Desert",  EngineConfig.BIOME_DESERT),
            new BiomeChoice("Savanna", EngineConfig.BIOME_SAVANNA),
            new BiomeChoice("Jungle",  EngineConfig.BIOME_JUNGLE),
            new BiomeChoice("Snow",    EngineConfig.BIOME_SNOW),
            new BiomeChoice("Swamp",   EngineConfig.BIOME_SWAMP),
        };

        return new JComboBox<>(choices);
    }

    private record BiomeChoice(String name, int biomeId) {
        @NotNull
        @Override
        public String toString() {
            return name;
        }
    }

    public record TeleportTarget(int x, int y, int z) {}
}