package server.view;

import javafx.scene.control.Labeled;
import org.kordamp.ikonli.javafx.FontIcon;

/** Creates consistent vector icons that render correctly on every supported JDK. */
public final class UiIcons {
    private UiIcons() {
    }

    public static FontIcon icon(String literal, String styleClass) {
        FontIcon icon = new FontIcon(literal);
        icon.getStyleClass().add(styleClass);
        return icon;
    }

    public static <T extends Labeled> T graphic(T control, String literal, String styleClass) {
        control.setGraphic(icon(literal, styleClass));
        control.setGraphicTextGap(8);
        return control;
    }
}
