package server.view;

import java.util.Random;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** Resolution-independent illustration drawn locally; no network image dependency. */
public final class CyberArtwork extends Region {
    private final Canvas canvas = new Canvas();
    private static final Color CYAN = Color.web("#00dfff");

    public CyberArtwork() {
        setMinSize(0, 0);
        getChildren().add(canvas);
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        double scale = Math.min(width / 780, height / 790);
        g.save();
        g.translate((width - 780 * scale) / 2, (height - 790 * scale) / 2);
        g.scale(scale, scale);
        drawWorld(g);
        drawRacks(g);
        drawRings(g);
        drawOperator(g);
        drawCaption(g);
        g.restore();
    }

    private void drawWorld(GraphicsContext g) {
        double[][] continents = {
            {40,145, 110,75, 225,65, 290,110, 242,166, 173,176, 145,234, 96,209},
            {192,241, 241,268, 273,328, 245,397, 211,420, 199,347, 168,290},
            {338,140, 400,95, 454,112, 486,154, 456,180, 409,170, 390,205, 346,183},
            {369,199, 459,187, 498,235, 473,306, 425,344, 386,285},
            {454,113, 546,79, 674,112, 743,172, 687,219, 626,202, 584,262, 529,198, 477,190},
            {637,310, 705,305, 735,351, 672,372, 628,350}
        };
        Random random = new Random(8128);
        for (double[] continent : continents) {
            for (int x = 32; x < 750; x += 6) {
                for (int y = 60; y < 430; y += 6) {
                    if (inside(x, y, continent)) {
                        g.setFill(Color.web(random.nextBoolean() ? "#087ce6" : "#0babe9", .6));
                        g.fillOval(x + random.nextDouble(), y + random.nextDouble(), 3, 3);
                    }
                }
            }
        }
        g.setStroke(Color.web("#03a7e5", .5));
        g.setLineWidth(1);
        double[][] nodes = {{80,170},{210,130},{370,186},{540,106},{648,193},{696,350},{219,346}};
        for (int i = 0; i < nodes.length - 1; i++) {
            double[] a = nodes[i], b = nodes[i + 1];
            g.beginPath();
            g.moveTo(a[0], a[1]);
            g.quadraticCurveTo((a[0] + b[0]) / 2, Math.min(a[1], b[1]) - 95, b[0], b[1]);
            g.stroke();
        }
        g.setEffect(new DropShadow(18, CYAN));
        g.setFill(Color.web("#a4ffff"));
        for (double[] point : nodes) g.fillOval(point[0] - 4, point[1] - 4, 8, 8);
        g.setEffect(null);
    }

    private boolean inside(double x, double y, double[] polygon) {
        boolean result = false;
        for (int i = 0, j = polygon.length - 2; i < polygon.length; j = i, i += 2) {
            if ((polygon[i + 1] > y) != (polygon[j + 1] > y)
                    && x < (polygon[j] - polygon[i]) * (y - polygon[i + 1])
                    / (polygon[j + 1] - polygon[i + 1]) + polygon[i]) result = !result;
        }
        return result;
    }

    private void drawRacks(GraphicsContext g) {
        for (int side = 0; side < 2; side++) {
            for (int rack = 0; rack < 3; rack++) {
                double x = side == 0 ? 10 + rack * 62 : 590 + rack * 62;
                double y = 358 + rack * (side == 0 ? 24 : -22);
                g.setFill(Color.web("#031a38"));
                g.setStroke(Color.web("#087ee7"));
                g.setLineWidth(2);
                g.fillRect(x, y, 54, 222);
                g.strokeRect(x, y, 54, 222);
                for (int row = 0; row < 8; row++) {
                    g.setFill(Color.web("#063b78"));
                    g.fillRoundRect(x + 6, y + 12 + row * 24, 42, 17, 2, 2);
                    g.setFill(CYAN);
                    g.fillRect(x + 11, y + 18 + row * 24, 4, 4);
                    g.setFill(Color.web("#008bff"));
                    g.fillRect(x + 22, y + 18 + row * 24, 19, 3);
                }
            }
        }
    }

    private void drawRings(GraphicsContext g) {
        g.setFill(Color.web("#002956", .52));
        g.fillOval(138, 155, 504, 504);
        g.setEffect(new DropShadow(18, Color.web("#008aff")));
        g.setStroke(CYAN);
        g.setLineWidth(4);
        g.strokeOval(150, 167, 480, 480);
        g.setStroke(Color.web("#086aff"));
        g.setLineWidth(11);
        g.strokeArc(167, 184, 446, 446, 3, 171, ArcType.OPEN);
        g.strokeArc(167, 184, 446, 446, 190, 157, ArcType.OPEN);
        g.setEffect(null);
        g.setStroke(Color.web("#1cd6ff"));
        g.setLineWidth(1.5);
        g.strokeOval(186, 203, 408, 408);
        g.setLineWidth(8);
        for (int angle = 0; angle < 360; angle += 12) {
            g.setStroke(angle % 24 == 0 ? Color.web("#0071e7") : Color.web("#00519a"));
            g.strokeArc(198, 215, 384, 384, angle, 7, ArcType.OPEN);
        }
        g.setStroke(Color.web("#00b9ff", .6));
        g.setLineWidth(1);
        for (int i = 0; i < 10; i++) {
            g.strokeLine(0, 596 + i * 18, 780, 596 + i * 18);
            g.strokeLine(390, 540, -500 + i * 190, 790);
        }
        g.setEffect(new DropShadow(12, Color.web("#008cff")));
        g.setStroke(CYAN);
        g.setLineWidth(3);
        g.strokeOval(143, 587, 494, 69);
        g.strokeOval(205, 706, 370, 49);
        g.setEffect(null);
    }

    private void drawOperator(GraphicsContext g) {
        g.setFill(new LinearGradient(0, 260, 0, 610, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#074485")), new Stop(.5, Color.web("#021127")),
                new Stop(1, Color.web("#010611"))));
        g.setStroke(Color.web("#00bbff"));
        g.setLineWidth(2.5);
        poly(g, 180,600, 213,502, 292,455, 273,382, 304,301, 390,242,
                476,301, 507,382, 488,455, 567,502, 600,600);
        g.setFill(Color.web("#01030c"));
        poly(g, 282,384, 332,353, 390,334, 448,353, 498,384,
                466,452, 390,494, 314,452);
        g.setStroke(Color.web("#1377ca"));
        g.setLineWidth(1.4);
        g.strokeLine(390,248, 390,331);
        g.strokeLine(295,457, 330,498);
        g.strokeLine(485,457, 450,498);
        g.setFill(CYAN);
        g.setEffect(new DropShadow(22, Color.web("#126aff")));
        poly(g, 328,392, 374,411, 359,421, 340,416);
        poly(g, 452,392, 406,411, 421,421, 440,416);
        g.setEffect(null);
        g.setFill(new LinearGradient(0, 495, 0, 607, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#12356b")), new Stop(1, Color.web("#02081d"))));
        g.setStroke(Color.web("#368bff"));
        g.setLineWidth(3);
        poly(g, 240,477, 540,477, 524,607, 256,607);
        g.setFill(Color.web("#002d66"));
        g.setStroke(CYAN);
        g.setEffect(new DropShadow(15, Color.web("#008dff")));
        poly(g, 390,505, 426,521, 423,560, 410,581, 390,595, 370,581, 357,560, 354,521);
        g.setEffect(null);
        g.setStroke(CYAN);
        g.setLineWidth(5);
        g.strokeRoundRect(379,533,22,25,16,16);
        g.setFill(CYAN);
        g.fillRoundRect(371,548,38,29,4,4);
        g.setFill(Color.web("#002454"));
        g.fillOval(386,556,8,8);
        g.fillRect(388,560,4,10);
        g.setFill(Color.web("#04112b"));
        g.fillOval(166,600,448,35);
        g.setStroke(CYAN);
        g.setLineWidth(3);
        g.strokeLine(174,610,606,610);
    }

    private void drawCaption(GraphicsContext g) {
        g.setFill(new LinearGradient(0, 634, 0, 708, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#03162f", .92)), new Stop(1, Color.web("#020816", .96))));
        poly(g, 45,631, 735,631, 759,655, 722,713, 58,713, 21,655);
        g.setStroke(Color.web("#088bda"));
        g.setLineWidth(1);
        g.strokeLine(65,713,715,713);
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 31));
        g.setFill(Color.web("#e3f8ff"));
        g.fillText("DoS/DDoS Protection Server", 390, 667);
        g.setFont(Font.font("Segoe UI", 16));
        g.setFill(Color.web("#70e8ff"));
        g.fillText("Bảo vệ hệ thống · Ngăn chặn tấn công · Đảm bảo an ninh mạng", 390, 697);
    }

    private void poly(GraphicsContext g, double... points) {
        double[] x = new double[points.length / 2], y = new double[x.length];
        for (int i = 0; i < x.length; i++) { x[i] = points[i * 2]; y[i] = points[i * 2 + 1]; }
        g.fillPolygon(x, y, x.length);
        g.strokePolygon(x, y, x.length);
    }
}
