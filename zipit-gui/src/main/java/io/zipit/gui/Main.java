package io.zipit.gui;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Desktop entry point. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Crisp text: enable system LCD anti-aliasing before the LAF loads.
        if (System.getProperty("awt.useSystemAAFontSettings") == null) {
            System.setProperty("awt.useSystemAAFontSettings", "on");
        }
        if (System.getProperty("swing.aatext") == null) {
            System.setProperty("swing.aatext", "true");
        }
        // Let FlatLaf draw the window chrome so light and dark stay cohesive.
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        Theme.install();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ZipitFrame().setVisible(true);
            }
        });
    }
}
