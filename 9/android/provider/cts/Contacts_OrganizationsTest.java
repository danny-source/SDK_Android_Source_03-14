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

package android.provider.cts;

import android.content.Context;
import android.provider.Contacts.Organizations;
import android.test.AndroidTestCase;
import dalvik.annotation.TestTargets;
import dalvik.annotation.TestTargetNew;
import dalvik.annotation.TestLevel;
import dalvik.annotation.TestTargetClass;

@TestTargetClass(android.provider.Contacts.Organizations.class)
public class Contacts_OrganizationsTest extends AndroidTestCase {
    @TestTargetNew(
        level = TestLevel.COMPLETE,
        notes = "Test getDisplayLabel(Context context, int type, CharSequence label",
        method = "getDisplayLabel",
        args = {android.content.Context.class, int.class, java.lang.CharSequence.class}
    )
    public void testGetDisplayLabel() {
        String label = "label";
        String display = Organizations.getDisplayLabel(getContext(),
                Organizations.TYPE_CUSTOM, label).toString();
        assertEquals(label, display);

        CharSequence[] labels = getContext().getResources().getTextArray(
                com.android.internal.R.array.organizationTypes);
        display = Organizations.getDisplayLabel(getContext(),
                Organizations.TYPE_OTHER, label).toString();
        assertEquals(labels[Organizations.TYPE_OTHER - 1], display);

        display = Organizations.getDisplayLabel(getContext(),
                Organizations.TYPE_WORK, label).toString();
        assertEquals(labels[Organizations.TYPE_WORK - 1], display);
    }
}
