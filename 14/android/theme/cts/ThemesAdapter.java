/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.theme.cts;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class ThemesAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    private ThemeInfo[] mThemes;

    public ThemesAdapter(Activity activity, ThemeInfo[] themes) {
        mThemes = themes;
        mInflater = activity.getLayoutInflater();
    }

    @Override
    public int getCount() {
        return mThemes.length;
    }

    @Override
    public Object getItem(int position) {
        return mThemes[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
        }

        ((TextView) convertView).setText(mThemes[position].getThemeName());

        return convertView;
    }

}
