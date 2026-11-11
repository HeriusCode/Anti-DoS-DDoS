package server.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/** Animated blue network background shared by the registration and login modes. */
final class ProtectionBackdrop extends Pane implements AutoCloseable {
    private static final Color GRID = Color.web("#07547c", .18);
    private static final Color LINK = Color.web("#00bce9", .20);
    private static final Color NODE = Color.web("#39ecff", .82);
    private final Canvas canvas = new Canvas();
    private final List<NetworkNode> nodes = new ArrayList<>();
    private final AnimationTimer animation;
    private long previousFrame;
    private double phase;

    ProtectionBackdrop() {
        setMouseTransparent(true);
        getChildren().add(canvas);
        Random random = new Random(4217);
        for (int index = 0; index < 58; index++) {
            nodes.add(new NetworkNode(random.nextDouble(), random.nextDouble(),
                    .015 + random.nextDouble() * .035,
                    random.nextDouble() * Math.PI * 2,
                    1.2 + random.nextDouble() * 2.2));
        }
        widthProperty().addListener((observable, oldValue, value) -> resizeCanvas());
        heightProperty().addListener((observable, oldValue, value) -> resizeCanvas());
        animation = new AnimationTimer() {
            @Override public void handle(long now) {
                if (previousFrame != 0 && now - previousFrame < 40_000_000L) return;
                previousFrame = now;
                phase += .035;
                draw();
            }
        };
        animation.start();
    }

    private void resizeCanvas() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    private void draw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        g.setStroke(GRID);
        g.setLineWidth(.7);
        for (double x = 24; x < width; x += 44) g.strokeLine(x, 0, x, height);
        for (double y = 20; y < height; y += 44) g.strokeLine(0, y, width, y);

        for (int first = 0; first < nodes.size(); first++) {
            NetworkNode a = nodes.get(first);
            double ax = a.x(width, phase), ay = a.y(height, phase);
            for (int second = first + 1; second < nodes.size(); second++) {
                NetworkNode b = nodes.get(second);
                double bx = b.x(width, phase), by = b.y(height, phase);
                double dx = ax - bx, dy = ay - by;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < 150) {
                    g.setStroke(Color.web("#00cfff", .23 * (1 - distance / 150)));
                    g.strokeLine(ax, ay, bx, by);
                }
            }
        }
        for (int index = 0; index < nodes.size(); index++) {
            NetworkNode node = nodes.get(index);
            double x = node.x(width, phase), y = node.y(height, phase);
            double pulse = node.radius + (Math.sin(phase * 2.3 + index) + 1) * .7;
            g.setFill(Color.web("#009fe8", .12));
            g.fillOval(x - pulse * 3, y - pulse * 3, pulse * 6, pulse * 6);
            g.setFill(NODE);
            g.fillOval(x - pulse / 2, y - pulse / 2, pulse, pulse);
        }
        drawFrame(g, width, height);
    }

    private void drawFrame(GraphicsContext g, double width, double height) {
        g.setStroke(Color.web("#00c9ef", .72));
        g.setLineWidth(1.4);
        double inset = 12;
        g.beginPath();
        g.moveTo(inset + 36, inset);
        g.lineTo(width * .31, inset);
        g.lineTo(width * .31 + 15, inset + 8);
        g.lineTo(width - inset - 50, inset + 8);
        g.lineTo(width - inset, inset + 48);
        g.lineTo(width - inset, height - inset - 34);
        g.lineTo(width - inset - 34, height - inset);
        g.lineTo(width * .68, height - inset);
        g.lineTo(width * .68 - 14, height - inset - 8);
        g.lineTo(inset + 34, height - inset - 8);
        g.lineTo(inset, height - inset - 42);
        g.lineTo(inset, inset + 36);
        g.closePath();
        g.stroke();

        g.setStroke(Color.web("#0874ba", .50));
        g.setLineWidth(5);
        g.strokeLine(width - 180, inset + 1, width - 115, inset + 1);
        g.strokeLine(inset + 2, height - 92, inset + 2, height - 43);
        g.setStroke(LINK);
        for (int index = 0; index < 5; index++) {
            double x = width - 250 + index * 19;
            g.strokeLine(x, inset + 17, x + 12, inset + 17);
        }
    }

    @Override public void close() {
        animation.stop();
    }

    private record NetworkNode(double baseX, double baseY, double amplitude, double offset, double radius) {
        double x(double width, double phase) {
            return (baseX + Math.sin(phase + offset) * amplitude) * width;
        }

        double y(double height, double phase) {
            return (baseY + Math.cos(phase * .82 + offset) * amplitude) * height;
        }
    }
}
