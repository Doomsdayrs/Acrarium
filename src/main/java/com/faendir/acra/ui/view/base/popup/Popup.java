/*
 * (C) Copyright 2018 Lukas Morawietz (https://github.com/F43nd1r)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.faendir.acra.ui.view.base.popup;

import com.faendir.acra.i18n.I18nButton;
import com.faendir.acra.i18n.Messages;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.UI;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.AcraTheme;
import org.springframework.data.util.Pair;
import org.springframework.lang.NonNull;
import org.vaadin.spring.i18n.I18N;
import org.vaadin.spring.i18n.support.Translatable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Lukas
 * @since 19.12.2017
 */
public class Popup extends Window implements Translatable {
    private final List<Component> components;
    private final Map<ValidatedField<?, ?>, Pair<Boolean, ValidatedField.Listener>> fields;
    private final List<Button> buttons;
    private final I18N i18n;
    private final String captionId;
    private final Object[] params;

    public Popup(I18N i18n, String captionId, Object... params) {
        this.i18n = i18n;
        this.captionId = captionId;
        this.params = params;
        components = new ArrayList<>();
        fields = new HashMap<>();
        buttons = new ArrayList<>();
        updateMessageStrings(i18n.getLocale());
    }

    @NonNull
    public Popup addCreateButton(@NonNull Consumer<Popup> onCreateAction) {
        return addCreateButton(onCreateAction, false);
    }

    @NonNull
    public Popup addCreateButton(@NonNull Consumer<Popup> onCreateAction, boolean closeAfter) {
        buttons.add(new I18nButton(event -> {
            onCreateAction.accept(this);
            if (closeAfter) {
                close();
            }
        }, i18n, Messages.CREATE));
        return this;
    }

    @NonNull
    public Popup addCloseButton() {
        buttons.add(new I18nButton(event -> close(), i18n, Messages.CLOSE));
        return this;
    }

    @NonNull
    public Popup addYesNoButtons(@NonNull Consumer<Popup> onYesAction) {
        return addYesNoButtons(onYesAction, false);
    }

    @NonNull
    public Popup addYesNoButtons(@NonNull Consumer<Popup> onYesAction, boolean closeAfter) {
        buttons.add(new I18nButton(event -> {
            onYesAction.accept(this);
            if (closeAfter) {
                close();
            }
        }, i18n, Messages.YES));
        buttons.add(new I18nButton(event -> close(), i18n, Messages.NO));
        return this;
    }

    @NonNull
    public Popup addComponent(@NonNull Component component) {
        components.add(component);
        return this;
    }

    public Popup addValidatedField(@NonNull ValidatedField<?, ?> validatedField) {
        return addValidatedField(validatedField, false);
    }

    public Popup addValidatedField(@NonNull ValidatedField<?, ?> validatedField, boolean isInitialValid) {
        ValidatedField.Listener listener = value -> updateField(validatedField, value);
        validatedField.addListener(listener);
        fields.put(validatedField, Pair.of(isInitialValid, listener));
        return addComponent(validatedField.getField());
    }

    private void updateField(@NonNull ValidatedField<?, ?> field, boolean value) {
        fields.put(field, Pair.of(value, fields.get(field).getSecond()));
        checkValid();
    }

    public Popup clear() {
        components.clear();
        fields.forEach((field, booleanListenerPair) -> field.removeListener(booleanListenerPair.getSecond()));
        fields.clear();
        buttons.clear();
        return this;
    }

    public void show() {
        if (buttons.size() == 1) {
            components.add(buttons.get(0));
        } else if (buttons.size() > 1) {
            HorizontalLayout buttonLayout = new HorizontalLayout();
            components.add(buttonLayout);
            buttons.forEach(buttonLayout::addComponent);
            buttons.forEach(button -> button.setWidth(100, Unit.PERCENTAGE));
        }
        components.forEach(component -> component.setWidth(100, Unit.PERCENTAGE));
        FormLayout layout = new FormLayout();
        components.forEach(layout::addComponent);
        checkValid();
        layout.addStyleNames(AcraTheme.PADDING_LEFT, AcraTheme.PADDING_RIGHT);
        setContent(layout);
        center();
        if (!isAttached()) {
            UI.getCurrent().addWindow(this);
        }
    }

    private void checkValid() {
        boolean valid = fields.values().stream().map(Pair::getFirst).reduce(Boolean::logicalAnd).orElse(true);
        buttons.forEach(button -> button.setEnabled(valid));
    }

    @Override
    public void updateMessageStrings(Locale locale) {
        setCaption(i18n.get(captionId, locale, params));
    }
}
