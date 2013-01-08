package org.codinjutsu.tools.mongo.view.style;

import com.intellij.ui.SimpleTextAttributes;

import java.awt.*;

public class DefaultTextAttributesProvider implements TextAttributesProvider {

    private static final Color LIGNT_GREEN = new Color(0, 128, 0);
    private static final Color PURPLE = new Color(102, 14, 122);
    private static final Color LIGHT_GRAY = new Color(128, 128, 128);

    private static final SimpleTextAttributes INDEX = new SimpleTextAttributes(Font.BOLD, Color.BLACK);
    private static final SimpleTextAttributes KEY_VALUE = new SimpleTextAttributes(Font.BOLD, PURPLE);
    private static final SimpleTextAttributes INTEGER_TEXT_ATTRIBUTE = new SimpleTextAttributes(Font.PLAIN, Color.BLUE);
    private static final SimpleTextAttributes BOOLEAN_TEXT_ATTRIBUTE = INTEGER_TEXT_ATTRIBUTE;
    private static final SimpleTextAttributes STRING_TEXT_ATTRIBUTE = new SimpleTextAttributes(Font.PLAIN, LIGNT_GREEN);
    private static final SimpleTextAttributes NULL_TEXT_ATTRIBUTE = new SimpleTextAttributes(Font.ITALIC, LIGHT_GRAY);
    private static final SimpleTextAttributes DBOBJECT_TEXT_ATTRIBUTE = new SimpleTextAttributes(Font.BOLD, LIGHT_GRAY);

    @Override
    public SimpleTextAttributes getIndexAttribute() {
        return INDEX;
    }

    @Override
    public SimpleTextAttributes getKeyValueAttribute() {
        return KEY_VALUE;
    }

    @Override
    public SimpleTextAttributes getIntegerAttribute() {
        return INTEGER_TEXT_ATTRIBUTE;
    }

    @Override
    public SimpleTextAttributes getBooleanAttribute() {
        return BOOLEAN_TEXT_ATTRIBUTE;
    }

    @Override
    public SimpleTextAttributes getStringAttribute() {
        return STRING_TEXT_ATTRIBUTE;
    }

    @Override
    public SimpleTextAttributes getNullAttribute() {
        return NULL_TEXT_ATTRIBUTE;
    }

    @Override
    public SimpleTextAttributes getDBObjectAttribute() {
        return DBOBJECT_TEXT_ATTRIBUTE;
    }
}
