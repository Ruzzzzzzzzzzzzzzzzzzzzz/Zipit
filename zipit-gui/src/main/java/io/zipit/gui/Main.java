package io.zipit.gui;

import javax.swing.SwingUtilities;

/** Desktop entry point. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Ui.install();
        SwingUtilities.invokeLater(() -> new ZipitFrame().setVisible(true));
    }
}
