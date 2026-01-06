package org.aouessar.renderer.ui;

import org.aouessar.shared.EngineConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class TeleportDialog {
    private TeleportDialog() {}

    /** Accepts "x z" or "x y z". Also accepts commas. */
    public static Optional<Vec3i> prompt(int currentY) {
        // Always run UI on the Swing EDT (important!)
        AtomicReference<Optional<Vec3i>> out = new AtomicReference<>(Optional.empty());

        Runnable task = () -> out.set(showDialog(currentY));

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

    private static Optional<Vec3i> showDialog(int currentY) {
        final JDialog dialog = new JDialog((Frame) null, "Teleport", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setAlwaysOnTop(true);

        final JTextField field = new JTextField(24);
        field.setText(""); // you can prefill with last coords if you want

        final JLabel hint = new JLabel("<html>Enter: <b>x z</b> (keeps y) or <b>x y z</b><br/>Example: 1200 -3400</html>");
        final JLabel err = new JLabel(" ");
        err.setForeground(Color.RED);

        final JButton ok = new JButton("Teleport");
        final JButton cancel = new JButton("Cancel");

        final AtomicReference<Optional<Vec3i>> result = new AtomicReference<>(Optional.empty());

        ok.addActionListener(ev -> {
            Optional<Vec3i> parsed = parse(field.getText(), currentY);
            if (parsed.isPresent()) {
                result.set(parsed);
                dialog.dispose(); // IMPORTANT: always dispose
            } else {
                err.setText("Invalid format. Use: x z  or  x y z");
            }
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

        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputRow.add(new JLabel("Coords:"));
        inputRow.add(field);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(hint);
        root.add(Box.createVerticalStrut(8));
        root.add(inputRow);
        root.add(Box.createVerticalStrut(6));
        root.add(err);
        root.add(Box.createVerticalStrut(8));
        root.add(buttons);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        // Focus the text field immediately
        SwingUtilities.invokeLater(field::requestFocusInWindow);

        dialog.setVisible(true); // blocks until disposed
        dialog.dispose(); // double-safety

        return result.get();
    }

    private static Optional<Vec3i> parse(String s, int currentY) {
        if (s == null) return Optional.empty();
        s = s.trim().replace(',', ' ');
        if (s.isEmpty()) return Optional.empty();

        String[] parts = s.split("\\s+");
        try {
            if (parts.length == 2) {
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[1]);
                return Optional.of(new Vec3i(x, clampY(currentY), z));
            }
            if (parts.length == 3) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                return Optional.of(new Vec3i(x, clampY(y), z));
            }
        } catch (NumberFormatException ignored) {}

        return Optional.empty();
    }

    private static int clampY(int y) {
        if (y < EngineConfig.MIN_Y) return EngineConfig.MIN_Y;
        if (y > EngineConfig.MAX_Y) return EngineConfig.MAX_Y;
        return y;
    }

    public record Vec3i(int x, int y, int z) {}
}