package server.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/** Compact on/off switch used by protection controls. */
public final class ToggleSwitch extends StackPane {
    private static final PseudoClass ON = PseudoClass.getPseudoClass("on");
    private final BooleanProperty selected = new SimpleBooleanProperty();
    private boolean dragged;

    public ToggleSwitch(boolean initialValue) {
        Circle thumb = new Circle(5.5);
        thumb.getStyleClass().add("switch-knob");
        getChildren().add(thumb);
        getStyleClass().add("switch-control");
        setMinSize(34, 18);
        setPrefSize(34, 18);
        setMaxSize(34, 18);
        setCursor(Cursor.HAND);
        setAccessibleRole(AccessibleRole.CHECK_BOX);

        selected.addListener((observable, oldValue, enabled) -> {
            pseudoClassStateChanged(ON, enabled);
            setAccessibleText(enabled ? "On" : "Off");
        });
        setSelected(initialValue);

        setOnMousePressed(event -> dragged = false);
        setOnMouseDragged(event -> dragged = true);
        setOnMouseReleased(event -> setSelected(dragged
                ? event.getX() >= getWidth() / 2
                : !isSelected()));
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }
}
