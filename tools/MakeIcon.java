import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the program icon of Blast: a round black bomb with a burning fuse in front of an orange burst of
 * flame, in a dark square with an orange border. Drawn in code, as the game draws its figures, as a PNG of
 * the given size.
 *
 * <pre>
 * java tools/MakeIcon.java 48 src/main/resources/icons/amade-blast-game.png
 * java tools/MakeIcon.java 256 build/icon-256.png
 * python3 -c "from PIL import Image; Image.open('build/icon-256.png').save('packaging/windows/amade-blast-game.ico',
 *     sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])"
 * </pre>
 *
 * The files are part of the repository; run this again only to change the icon.
 */
public class MakeIcon {
    public static void main(String[] args) throws IOException {
        int size = Integer.parseInt(args[0]);
        File file = new File(args[1]);
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        ImageIO.write(icon(size), "png", file);
    }

    static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 48.0, size / 48.0);
        RoundRectangle2D.Double back = new RoundRectangle2D.Double(2, 2, 44, 44, 10, 10);
        g.setPaint(new GradientPaint(0, 2, new Color(0x2A2430), 0, 46, new Color(0x0C0A10)));
        g.fill(back);
        g.setColor(new Color(0xFF8A1E));
        g.setStroke(new BasicStroke(2f));
        g.draw(back);
        // The burst behind the bomb: a star of twelve points, orange outside, yellow inside.
        g.setClip(back);
        g.setColor(new Color(0xFF5A14));
        g.fill(star(24, 26, 21, 11, 12));
        g.setColor(new Color(0xFFD23C));
        g.fill(star(24, 26, 14, 8, 12));
        g.setClip(null);
        // The bomb, lit from the top left, with its cap.
        g.setPaint(new RadialGradientPaint(19, 24, 14, new float[]{0, 1},
                new Color[]{new Color(0x5A5A70), new Color(0x101018)}));
        g.fill(new Ellipse2D.Double(10, 18, 24, 24));
        g.setColor(new Color(0x404050));
        g.fill(new RoundRectangle2D.Double(23, 15.5, 7, 6, 2, 2));
        g.setColor(new Color(255, 255, 255, 150));
        g.fill(new Ellipse2D.Double(15, 23, 5, 4));
        // The fuse and its spark.
        g.setColor(new Color(0xC8A878));
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new QuadCurve2D.Double(27, 16, 29, 9, 35, 9));
        g.setColor(new Color(0xFFF4B0));
        g.fill(star(36, 9, 5, 2, 6));
        g.dispose();
        return image;
    }

    private static Path2D.Double star(double cx, double cy, double outer, double inner, int points) {
        Path2D.Double star = new Path2D.Double();
        for (int i = 0; i < 2 * points; i++) {
            double r = i % 2 == 0 ? outer : inner;
            double a = Math.PI * i / points - Math.PI / 2;
            if (i == 0) star.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
            else star.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
        }
        star.closePath();
        return star;
    }
}
