/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.draw9patch.ui.action;

import com.android.draw9patch.ui.MainFrame;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.Toolkit;

public class SaveAction extends BackgroundAction {
    public static final String ACTION_NAME = "save";
    private MainFrame frame;

    public SaveAction(MainFrame frame) {
        this.frame = frame;
        putValue(NAME, "Save 9-patch...");
        putValue(SHORT_DESCRIPTION, "Save...");
        putValue(LONG_DESCRIPTION, "Save 9-patch...");
        putValue(MNEMONIC_KEY, KeyEvent.VK_S);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    }

    public void actionPerformed(ActionEvent e) {
        executeBackgroundTask(frame.save());
    }
}
