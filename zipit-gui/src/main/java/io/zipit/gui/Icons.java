package io.zipit.gui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/**
 * Hand-drawn vector icon set (24x24 design grid) used across the whole UI.
 *
 * <p>Keeping icons vector means they stay crisp on HiDPI displays and follow
 * the theme palette without shipping image assets.</p>
 */
final class Icons {

    private Icons() {
    }

    /** Immutable icon with a fixed color. */
    static Icon icon(final String name, final int size, final Color color) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                paint(g2, name, x, y, size, color, 1.7f);
                g2.dispose();
            }
        };
    }

    /** Icon whose color follows the active theme. */
    static Icon themeIcon(final String name, final int size, final Supplier<Color> color) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                paint(g2, name, x, y, size, color.get(), 1.7f);
                g2.dispose();
            }
        };
    }

    /** Paints an icon into an arbitrary rectangle. */
    static void paint(Graphics2D g, String name, int x, int y, int size, Color color, float stroke) {
        if (name == null || color == null || size <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        double scale = size / 24.0;
        g2.scale(scale, scale);
        g2.setColor(color);
        // Requested stroke width is expressed in device pixels; convert back to the
        // 24x24 design grid so small and large icons keep a consistent weight.
        float lineWidth = Math.max(0.7f, stroke / (float) scale);
        g2.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        draw(g2, name);
        g2.dispose();
    }

    private static void draw(Graphics2D g, String name) {
        if ("dashboard".equals(name)) {
            g.draw(new RoundRectangle2D.Double(3, 3, 7.5, 7.5, 2.4, 2.4));
            g.draw(new RoundRectangle2D.Double(13.5, 3, 7.5, 7.5, 2.4, 2.4));
            g.draw(new RoundRectangle2D.Double(3, 13.5, 7.5, 7.5, 2.4, 2.4));
            g.draw(new RoundRectangle2D.Double(13.5, 13.5, 7.5, 7.5, 2.4, 2.4));
        } else if ("transforms".equals(name)) {
            g.draw(new Line2D.Double(3, 6.5, 21, 6.5));
            g.draw(new Line2D.Double(3, 12, 21, 12));
            g.draw(new Line2D.Double(3, 17.5, 21, 17.5));
            g.fill(new Ellipse2D.Double(7, 4.3, 4.4, 4.4));
            g.fill(new Ellipse2D.Double(14.2, 9.8, 4.4, 4.4));
            g.fill(new Ellipse2D.Double(5.5, 15.3, 4.4, 4.4));
        } else if ("rules".equals(name)) {
            g.draw(new RoundRectangle2D.Double(4, 2.8, 16, 18.4, 3, 3));
            g.draw(new Line2D.Double(8, 8, 16, 8));
            g.draw(new Line2D.Double(8, 12, 16, 12));
            g.draw(new Line2D.Double(8, 16, 13, 16));
        } else if ("enterprise".equals(name)) {
            GeneralPath shield = new GeneralPath();
            shield.moveTo(12, 2.6);
            shield.lineTo(20.2, 6.2);
            shield.lineTo(20.2, 11.4);
            shield.curveTo(20.2, 16.4, 16.8, 19.8, 12, 21.4);
            shield.curveTo(7.2, 19.8, 3.8, 16.4, 3.8, 11.4);
            shield.lineTo(3.8, 6.2);
            shield.closePath();
            g.draw(shield);
            g.draw(new Line2D.Double(8.6, 11.8, 11.1, 14.2));
            g.draw(new Line2D.Double(11.1, 14.2, 15.6, 9.4));
        } else if ("console".equals(name)) {
            g.draw(new RoundRectangle2D.Double(2.8, 4, 18.4, 16, 3.2, 3.2));
            g.draw(new Line2D.Double(7, 9.4, 10.2, 12.2));
            g.draw(new Line2D.Double(10.2, 12.2, 7, 15));
            g.draw(new Line2D.Double(12.4, 15.2, 17, 15.2));
        } else if ("play".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(7.6, 4.4);
            p.lineTo(19, 12);
            p.lineTo(7.6, 19.6);
            p.closePath();
            g.fill(p);
        } else if ("stop".equals(name)) {
            g.fill(new RoundRectangle2D.Double(6, 6, 12, 12, 2.2, 2.2));
        } else if ("sun".equals(name)) {
            g.draw(new Ellipse2D.Double(8.4, 8.4, 7.2, 7.2));
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8;
                double x1 = 12 + Math.cos(angle) * 8.6;
                double y1 = 12 + Math.sin(angle) * 8.6;
                double x2 = 12 + Math.cos(angle) * 10.6;
                double y2 = 12 + Math.sin(angle) * 10.6;
                g.draw(new Line2D.Double(x1, y1, x2, y2));
            }
        } else if ("moon".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(15.6, 3.4);
            p.curveTo(10.2, 4.6, 7.2, 8.2, 7.8, 12.6);
            p.curveTo(8.4, 17, 12.4, 20.2, 17.4, 20.4);
            p.curveTo(13.4, 18.6, 10.8, 15.4, 10.8, 11.6);
            p.curveTo(10.8, 8.2, 12.6, 5.2, 15.6, 3.4);
            p.closePath();
            g.fill(p);
        } else if ("folder".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(3, 7.2);
            p.lineTo(3, 18.4);
            p.curveTo(3, 19.4, 3.8, 20.2, 4.8, 20.2);
            p.lineTo(19.2, 20.2);
            p.curveTo(20.2, 20.2, 21, 19.4, 21, 18.4);
            p.lineTo(21, 9.2);
            p.curveTo(21, 8.2, 20.2, 7.4, 19.2, 7.4);
            p.lineTo(11.6, 7.4);
            p.lineTo(9.6, 4.8);
            p.lineTo(4.8, 4.8);
            p.curveTo(3.8, 4.8, 3, 5.6, 3, 6.4);
            p.closePath();
            g.draw(p);
        } else if ("file".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(6, 2.8);
            p.lineTo(14.4, 2.8);
            p.lineTo(19, 7.4);
            p.lineTo(19, 21.2);
            p.lineTo(6, 21.2);
            p.closePath();
            g.draw(p);
            g.draw(new Line2D.Double(14, 2.9, 14, 7.8));
            g.draw(new Line2D.Double(14, 7.8, 18.8, 7.8));
        } else if ("upload".equals(name)) {
            g.draw(new Line2D.Double(12, 16, 12, 4.4));
            g.draw(new Line2D.Double(12, 4.4, 7.2, 9.2));
            g.draw(new Line2D.Double(12, 4.4, 16.8, 9.2));
            GeneralPath tray = new GeneralPath();
            tray.moveTo(4.2, 14.4);
            tray.lineTo(4.2, 18.2);
            tray.curveTo(4.2, 19.2, 5, 20, 6, 20);
            tray.lineTo(18, 20);
            tray.curveTo(19, 20, 19.8, 19.2, 19.8, 18.2);
            tray.lineTo(19.8, 14.4);
            g.draw(tray);
        } else if ("download".equals(name)) {
            g.draw(new Line2D.Double(12, 4.4, 12, 16));
            g.draw(new Line2D.Double(12, 16, 7.2, 11.2));
            g.draw(new Line2D.Double(12, 16, 16.8, 11.2));
            GeneralPath tray = new GeneralPath();
            tray.moveTo(4.2, 14.4);
            tray.lineTo(4.2, 18.2);
            tray.curveTo(4.2, 19.2, 5, 20, 6, 20);
            tray.lineTo(18, 20);
            tray.curveTo(19, 20, 19.8, 19.2, 19.8, 18.2);
            tray.lineTo(19.8, 14.4);
            g.draw(tray);
        } else if ("search".equals(name)) {
            g.draw(new Ellipse2D.Double(4, 4, 11.6, 11.6));
            g.draw(new Line2D.Double(14.8, 14.8, 20, 20));
        } else if ("trash".equals(name)) {
            g.draw(new Line2D.Double(3.8, 6.4, 20.2, 6.4));
            g.draw(new Line2D.Double(9, 6.2, 9, 3.8));
            g.draw(new Line2D.Double(15, 6.2, 15, 3.8));
            GeneralPath can = new GeneralPath();
            can.moveTo(5.4, 6.6);
            can.lineTo(6.6, 20.2);
            can.lineTo(17.4, 20.2);
            can.lineTo(18.6, 6.6);
            g.draw(can);
            g.draw(new Line2D.Double(10, 10.2, 10, 16.6));
            g.draw(new Line2D.Double(14, 10.2, 14, 16.6));
        } else if ("copy".equals(name)) {
            g.draw(new RoundRectangle2D.Double(8.4, 8.4, 12, 12, 2.6, 2.6));
            GeneralPath back = new GeneralPath();
            back.moveTo(15.6, 5.6);
            back.lineTo(15.6, 4.6);
            back.curveTo(15.6, 3.6, 14.8, 2.8, 13.8, 2.8);
            back.lineTo(5.8, 2.8);
            back.curveTo(4.8, 2.8, 4, 3.6, 4, 4.6);
            back.lineTo(4, 15.4);
            back.curveTo(4, 16.4, 4.8, 17.2, 5.8, 17.2);
            back.lineTo(6.6, 17.2);
            g.draw(back);
        } else if ("save".equals(name)) {
            g.draw(new RoundRectangle2D.Double(3.4, 3.4, 17.2, 17.2, 3, 3));
            g.draw(new RoundRectangle2D.Double(7.4, 3.6, 9.2, 5.6, 1.2, 1.2));
            g.draw(new RoundRectangle2D.Double(7.2, 13.2, 9.6, 7.2, 1.4, 1.4));
        } else if ("external".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(13.6, 4.2);
            p.lineTo(19.8, 4.2);
            p.lineTo(19.8, 10.4);
            g.draw(p);
            g.draw(new Line2D.Double(19.6, 4.4, 11.4, 12.6));
            GeneralPath box = new GeneralPath();
            box.moveTo(9.2, 5.4);
            box.lineTo(5, 5.4);
            box.curveTo(4.2, 5.4, 3.6, 6, 3.6, 6.8);
            box.lineTo(3.6, 18.8);
            box.curveTo(3.6, 19.6, 4.2, 20.2, 5, 20.2);
            box.lineTo(17, 20.2);
            box.curveTo(17.8, 20.2, 18.4, 19.6, 18.4, 18.8);
            box.lineTo(18.4, 14.6);
            g.draw(box);
        } else if ("info".equals(name)) {
            g.draw(new Ellipse2D.Double(2.8, 2.8, 18.4, 18.4));
            g.draw(new Line2D.Double(12, 10.8, 12, 17.2));
            g.fill(new Ellipse2D.Double(11.1, 6.4, 1.9, 1.9));
        } else if ("check".equals(name)) {
            g.draw(new Line2D.Double(4.6, 12.4, 9.6, 17.2));
            g.draw(new Line2D.Double(9.6, 17.2, 19.4, 6.8));
        } else if ("check-circle".equals(name)) {
            g.draw(new Ellipse2D.Double(2.8, 2.8, 18.4, 18.4));
            g.draw(new Line2D.Double(7.2, 12.2, 10.8, 15.8));
            g.draw(new Line2D.Double(10.8, 15.8, 16.8, 8.8));
        } else if ("x-circle".equals(name)) {
            g.draw(new Ellipse2D.Double(2.8, 2.8, 18.4, 18.4));
            g.draw(new Line2D.Double(8.6, 8.6, 15.4, 15.4));
            g.draw(new Line2D.Double(15.4, 8.6, 8.6, 15.4));
        } else if ("close".equals(name)) {
            g.draw(new Line2D.Double(6.4, 6.4, 17.6, 17.6));
            g.draw(new Line2D.Double(17.6, 6.4, 6.4, 17.6));
        } else if ("alert".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(12, 3.2);
            p.lineTo(21.4, 20.2);
            p.lineTo(2.6, 20.2);
            p.closePath();
            g.draw(p);
            g.draw(new Line2D.Double(12, 9.2, 12, 14.2));
            g.fill(new Ellipse2D.Double(11.1, 16.2, 1.8, 1.8));
        } else if ("chevron-down".equals(name)) {
            g.draw(new Line2D.Double(6, 9.4, 12, 15.4));
            g.draw(new Line2D.Double(12, 15.4, 18, 9.4));
        } else if ("chevron-right".equals(name)) {
            g.draw(new Line2D.Double(9.4, 6, 15.4, 12));
            g.draw(new Line2D.Double(15.4, 12, 9.4, 18));
        } else if ("arrow-right".equals(name)) {
            g.draw(new Line2D.Double(4, 12, 19.4, 12));
            g.draw(new Line2D.Double(13.4, 6, 19.4, 12));
            g.draw(new Line2D.Double(19.4, 12, 13.4, 18));
        } else if ("zap".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(13.2, 2.4);
            p.lineTo(4.6, 13.6);
            p.lineTo(10.6, 13.6);
            p.lineTo(9.4, 21.6);
            p.lineTo(19.4, 10.2);
            p.lineTo(12.8, 10.2);
            p.closePath();
            g.fill(p);
        } else if ("layers".equals(name)) {
            GeneralPath top = new GeneralPath();
            top.moveTo(12, 3);
            top.lineTo(21, 7.8);
            top.lineTo(12, 12.6);
            top.lineTo(3, 7.8);
            top.closePath();
            g.draw(top);
            g.draw(new Line2D.Double(3, 12.2, 12, 17));
            g.draw(new Line2D.Double(21, 12.2, 12, 17));
            g.draw(new Line2D.Double(3, 16.2, 12, 21));
            g.draw(new Line2D.Double(21, 16.2, 12, 21));
        } else if ("lock".equals(name)) {
            g.draw(new RoundRectangle2D.Double(4.6, 10.2, 14.8, 10.8, 2.6, 2.6));
            g.draw(new Arc2D.Double(7.4, 3.4, 9.2, 12, 0, 180, Arc2D.OPEN));
            g.fill(new Ellipse2D.Double(10.9, 14.2, 2.2, 2.2));
        } else if ("key".equals(name)) {
            g.draw(new Ellipse2D.Double(3, 8.6, 8.2, 8.2));
            g.draw(new Line2D.Double(9.6, 14.8, 20.6, 3.8));
            g.draw(new Line2D.Double(16.6, 7.8, 19.4, 10.6));
            g.draw(new Line2D.Double(13.2, 11.2, 15.6, 13.6));
        } else if ("server".equals(name)) {
            g.draw(new RoundRectangle2D.Double(3.2, 3.4, 17.6, 7.2, 2.2, 2.2));
            g.draw(new RoundRectangle2D.Double(3.2, 13.4, 17.6, 7.2, 2.2, 2.2));
            g.fill(new Ellipse2D.Double(6.2, 6.2, 1.8, 1.8));
            g.fill(new Ellipse2D.Double(6.2, 16.2, 1.8, 1.8));
            g.draw(new Line2D.Double(10.6, 7, 17, 7));
            g.draw(new Line2D.Double(10.6, 17, 17, 17));
        } else if ("refresh".equals(name)) {
            g.draw(new Arc2D.Double(3.4, 3.4, 17.2, 17.2, 42, 250, Arc2D.OPEN));
            GeneralPath head = new GeneralPath();
            head.moveTo(18.6, 2.6);
            head.lineTo(19.4, 8.4);
            head.lineTo(13.8, 7.2);
            g.draw(head);
        } else if ("plus".equals(name)) {
            g.draw(new Line2D.Double(12, 5, 12, 19));
            g.draw(new Line2D.Double(5, 12, 19, 12));
        } else if ("settings".equals(name)) {
            g.draw(new Ellipse2D.Double(8.8, 8.8, 6.4, 6.4));
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8;
                double cx = 12 + Math.cos(angle) * 8.2;
                double cy = 12 + Math.sin(angle) * 8.2;
                g.fill(new Ellipse2D.Double(cx - 1.5, cy - 1.5, 3, 3));
            }
        } else if ("activity".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(2.6, 12.4);
            p.lineTo(7, 12.4);
            p.lineTo(9.6, 4.8);
            p.lineTo(14, 19.2);
            p.lineTo(16.6, 12.4);
            p.lineTo(21.4, 12.4);
            g.draw(p);
        } else if ("cpu".equals(name)) {
            g.draw(new RoundRectangle2D.Double(6.4, 6.4, 11.2, 11.2, 2.4, 2.4));
            g.draw(new RoundRectangle2D.Double(9.8, 9.8, 4.4, 4.4, 1, 1));
            for (int i = 0; i < 3; i++) {
                double offset = 8.4 + i * 3.6;
                g.draw(new Line2D.Double(offset, 3.2, offset, 6.4));
                g.draw(new Line2D.Double(offset, 17.6, offset, 20.8));
                g.draw(new Line2D.Double(3.2, offset, 6.4, offset));
                g.draw(new Line2D.Double(17.6, offset, 20.8, offset));
            }
        } else if ("ghost".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(4.6, 20.4);
            p.lineTo(4.6, 10.4);
            p.curveTo(4.6, 6.2, 7.9, 3.4, 12, 3.4);
            p.curveTo(16.1, 3.4, 19.4, 6.2, 19.4, 10.4);
            p.lineTo(19.4, 20.4);
            p.lineTo(16.2, 17.8);
            p.lineTo(13.4, 20.4);
            p.lineTo(10.6, 17.8);
            p.lineTo(7.8, 20.4);
            p.closePath();
            g.draw(p);
            g.fill(new Ellipse2D.Double(8.8, 9.4, 1.8, 1.8));
            g.fill(new Ellipse2D.Double(13.4, 9.4, 1.8, 1.8));
        } else if ("sparkles".equals(name)) {
            fillStar(g, 9.4, 3.2, 2.6);
            fillStar(g, 17.4, 11.4, 2.1);
            fillStar(g, 8.2, 16.6, 1.7);
        } else if ("code".equals(name)) {
            g.draw(new Line2D.Double(8.4, 7.6, 3.6, 12));
            g.draw(new Line2D.Double(3.6, 12, 8.4, 16.4));
            g.draw(new Line2D.Double(15.6, 7.6, 20.4, 12));
            g.draw(new Line2D.Double(20.4, 12, 15.6, 16.4));
            g.draw(new Line2D.Double(13.4, 4.6, 10.6, 19.4));
        } else if ("database".equals(name)) {
            g.draw(new Ellipse2D.Double(4, 2.8, 16, 5.4));
            g.draw(new Arc2D.Double(4, 8.2, 16, 5.4, 180, 180, Arc2D.OPEN));
            g.draw(new Arc2D.Double(4, 13.6, 16, 5.4, 180, 180, Arc2D.OPEN));
            g.draw(new Line2D.Double(4, 5.4, 4, 19));
            g.draw(new Line2D.Double(20, 5.4, 20, 19));
            g.draw(new Arc2D.Double(4, 16.2, 16, 5.4, 180, 180, Arc2D.OPEN));
        } else if ("clock".equals(name)) {
            g.draw(new Ellipse2D.Double(2.8, 2.8, 18.4, 18.4));
            g.draw(new Line2D.Double(12, 7, 12, 12.4));
            g.draw(new Line2D.Double(12, 12.4, 16, 14.6));
        } else if ("hash".equals(name)) {
            g.draw(new Line2D.Double(9.4, 3.6, 7.4, 20.4));
            g.draw(new Line2D.Double(16.6, 3.6, 14.6, 20.4));
            g.draw(new Line2D.Double(4.2, 8.8, 20.4, 8.8));
            g.draw(new Line2D.Double(3.6, 15.2, 19.8, 15.2));
        } else if ("shuffle".equals(name)) {
            g.draw(new Line2D.Double(3, 7, 8, 7));
            g.draw(new Line2D.Double(8, 7, 16, 17));
            g.draw(new Line2D.Double(16, 17, 20.4, 17));
            g.draw(new Line2D.Double(3, 17, 8, 17));
            g.draw(new Line2D.Double(8, 17, 16, 7));
            g.draw(new Line2D.Double(16, 7, 20.4, 7));
            g.draw(new Line2D.Double(18, 4.6, 20.4, 7));
            g.draw(new Line2D.Double(18, 9.4, 20.4, 7));
            g.draw(new Line2D.Double(18, 14.6, 20.4, 17));
            g.draw(new Line2D.Double(18, 19.4, 20.4, 17));
        } else if ("box".equals(name)) {
            GeneralPath p = new GeneralPath();
            p.moveTo(12, 2.8);
            p.lineTo(20.6, 7.4);
            p.lineTo(20.6, 16.6);
            p.lineTo(12, 21.2);
            p.lineTo(3.4, 16.6);
            p.lineTo(3.4, 7.4);
            p.closePath();
            g.draw(p);
            g.draw(new Line2D.Double(3.6, 7.5, 12, 12.1));
            g.draw(new Line2D.Double(20.4, 7.5, 12, 12.1));
            g.draw(new Line2D.Double(12, 12.1, 12, 20.9));
        } else if ("diff".equals(name)) {
            g.draw(new RoundRectangle2D.Double(3, 3, 9.4, 9.4, 2.2, 2.2));
            g.draw(new RoundRectangle2D.Double(11.6, 11.6, 9.4, 9.4, 2.2, 2.2));
            g.draw(new Line2D.Double(5.2, 7.7, 10.2, 7.7));
            g.draw(new Line2D.Double(13.8, 16.3, 18.8, 16.3));
        } else if ("gauge".equals(name)) {
            g.draw(new Arc2D.Double(3.4, 4.4, 17.2, 17.2, 0, 180, Arc2D.OPEN));
            g.draw(new Line2D.Double(12, 13.4, 16.4, 8.2));
            g.fill(new Ellipse2D.Double(10.9, 12.3, 2.2, 2.2));
        } else if ("fingerprint".equals(name)) {
            g.draw(new Arc2D.Double(4, 7, 16, 12, 20, 140, Arc2D.OPEN));
            g.draw(new Arc2D.Double(7, 9.6, 10, 8, 20, 140, Arc2D.OPEN));
            g.draw(new Line2D.Double(12, 17, 12, 20.6));
            g.draw(new Line2D.Double(8.4, 17.2, 8.4, 20.4));
            g.draw(new Line2D.Double(15.6, 17.2, 15.6, 19.6));
        } else if ("globe".equals(name)) {
            g.draw(new Ellipse2D.Double(3, 3, 18, 18));
            g.draw(new Line2D.Double(3, 12, 21, 12));
            g.draw(new Ellipse2D.Double(7.6, 3, 8.8, 18));
            g.draw(new Line2D.Double(12, 3, 12, 21));
        } else if ("menu".equals(name)) {
            g.draw(new Line2D.Double(4, 6.6, 20, 6.6));
            g.draw(new Line2D.Double(4, 12, 20, 12));
            g.draw(new Line2D.Double(4, 17.4, 20, 17.4));
        } else if ("wand".equals(name)) {
            g.draw(new Line2D.Double(4.6, 19.4, 14.6, 9.4));
            g.draw(new Line2D.Double(13.4, 8.2, 15.8, 10.6));
            fillStar(g, 18.4, 5.6, 2.4);
            fillStar(g, 19.6, 15.2, 1.6);
            fillStar(g, 9, 4.4, 1.6);
        } else if ("list".equals(name)) {
            g.draw(new Line2D.Double(8.4, 6.4, 20, 6.4));
            g.draw(new Line2D.Double(8.4, 12, 20, 12));
            g.draw(new Line2D.Double(8.4, 17.6, 20, 17.6));
            g.fill(new Ellipse2D.Double(3.8, 5.5, 1.8, 1.8));
            g.fill(new Ellipse2D.Double(3.8, 11.1, 1.8, 1.8));
            g.fill(new Ellipse2D.Double(3.8, 16.7, 1.8, 1.8));
        } else if ("scissors".equals(name)) {
            g.draw(new Ellipse2D.Double(3.2, 15.2, 5.4, 5.4));
            g.draw(new Ellipse2D.Double(15.4, 15.2, 5.4, 5.4));
            g.draw(new Line2D.Double(7.8, 15.6, 18.4, 3.6));
            g.draw(new Line2D.Double(16.2, 15.6, 5.6, 3.6));
        } else if ("dot".equals(name)) {
            g.fill(new Ellipse2D.Double(9, 9, 6, 6));
        } else {
            // Fallback: a small filled circle so nothing renders as a blank gap.
            g.fill(new Ellipse2D.Double(8, 8, 8, 8));
        }
    }

    private static void fillStar(Graphics2D g, double cx, double cy, double radius) {
        GeneralPath p = new GeneralPath();
        p.moveTo(cx, cy - radius);
        p.quadTo(cx + radius * 0.22, cy - radius * 0.22, cx + radius, cy);
        p.quadTo(cx + radius * 0.22, cy + radius * 0.22, cx, cy + radius);
        p.quadTo(cx - radius * 0.22, cy + radius * 0.22, cx - radius, cy);
        p.quadTo(cx - radius * 0.22, cy - radius * 0.22, cx, cy - radius);
        p.closePath();
        g.fill(p);
    }
}
