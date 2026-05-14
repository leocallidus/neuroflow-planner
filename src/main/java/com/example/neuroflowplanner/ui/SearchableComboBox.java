package com.example.neuroflowplanner.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to add search/filter functionality to a ComboBox.
 * When the user types in an editable ComboBox, the dropdown list is filtered
 * to show only matching items.
 */
public class SearchableComboBox {

    /**
     * Makes a ComboBox searchable by filtering items as the user types.
     * The ComboBox must be editable.
     *
     * @param comboBox the ComboBox to make searchable
     * @param <T> the type of items in the ComboBox
     */
    public static <T> void makeSearchable(ComboBox<T> comboBox) {
        if (!comboBox.isEditable()) {
            comboBox.setEditable(true);
        }

        ObservableList<T> originalItems = FXCollections.observableArrayList(comboBox.getItems());
        TextField editor = comboBox.getEditor();

        editor.textProperty().addListener((obs, oldText, newText) -> {
            // Don't filter if the text matches a selected item
            T selected = comboBox.getValue();
            if (selected != null && selected.toString().equals(newText)) {
                return;
            }

            Platform.runLater(() -> {
                if (newText == null || newText.isEmpty()) {
                    // Show all items when text is empty
                    comboBox.getItems().setAll(originalItems);
                } else {
                    // Filter items
                    String lowerFilter = newText.toLowerCase();
                    List<T> filtered = new ArrayList<>();
                    for (T item : originalItems) {
                        if (item.toString().toLowerCase().contains(lowerFilter)) {
                            filtered.add(item);
                        }
                    }
                    comboBox.getItems().setAll(filtered);
                }

                // Show dropdown if there are items and we're focused
                if (!comboBox.getItems().isEmpty() && editor.isFocused()) {
                    if (!comboBox.isShowing()) {
                        comboBox.show();
                    }
                }
            });
        });

        // Handle keyboard navigation
        editor.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP) {
                if (!comboBox.isShowing()) {
                    comboBox.show();
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                comboBox.hide();
            } else if (event.getCode() == KeyCode.ENTER) {
                // Select the first filtered item if available
                if (!comboBox.getItems().isEmpty() && comboBox.getValue() == null) {
                    comboBox.setValue(comboBox.getItems().get(0));
                }
                comboBox.hide();
            }
        });

        // Restore original items when focus is lost
        comboBox.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                // Restore all items but keep the current value
                T currentValue = comboBox.getValue();
                String editorText = editor.getText();
                
                Platform.runLater(() -> {
                    comboBox.getItems().setAll(originalItems);
                    
                    // If editor has text that's not in items, keep it
                    if (currentValue != null) {
                        comboBox.setValue(currentValue);
                    } else if (editorText != null && !editorText.isEmpty()) {
                        editor.setText(editorText);
                    }
                });
            }
        });
    }

    /**
     * Updates the original items list for a searchable ComboBox.
     * Call this when you add new items to the ComboBox after making it searchable.
     *
     * @param comboBox the searchable ComboBox
     * @param newItems the new list of items
     * @param <T> the type of items
     */
    public static <T> void updateItems(ComboBox<T> comboBox, List<T> newItems) {
        comboBox.getItems().setAll(newItems);
        // Re-apply searchable to update the original items reference
        makeSearchable(comboBox);
    }

    /**
     * Creates a new searchable ComboBox with the given items.
     *
     * @param items the items for the ComboBox
     * @param <T> the type of items
     * @return a new searchable ComboBox
     */
    public static <T> ComboBox<T> create(List<T> items) {
        ComboBox<T> comboBox = new ComboBox<>();
        comboBox.setEditable(true);
        comboBox.getItems().addAll(items);
        makeSearchable(comboBox);
        return comboBox;
    }
}
