/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.homepage;
import android.content.res.Resources;

import android.animation.LayoutTransition;
import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.appbar.AppBarLayout;

import com.android.internal.util.UserIcons;

import com.android.settings.R;
import com.android.settings.accounts.AvatarViewMixin;
import com.android.settings.core.HideNonSystemOverlayMixin;
import com.android.settings.homepage.contextualcards.ContextualCardsFragment;
import com.android.settings.overlay.FeatureFactory;

import com.android.settingslib.drawable.CircleFramedDrawable;

import java.util.Random;
import java.util.Calendar;

public class SettingsHomepageActivity extends FragmentActivity {

    Context context;
    UserManager mUserManager;

    ImageView toolbarAvatar;
    AppBarLayout appBarLayout;
    View searchBar;
    View homepageSpacer;
    View homepageMainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings_homepage_container);
        final View root = findViewById(R.id.settings_homepage_container);
        root.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        setHomepageContainerPaddingTop();
	getRandomName();
	goodVibesPlease();

        Context context = getApplicationContext();

        mUserManager = context.getSystemService(UserManager.class);

        final Toolbar toolbar = findViewById(R.id.search_action_bar);
        FeatureFactory.getFactory(this).getSearchFeatureProvider()
                .initSearchToolbar(this /* activity */, toolbar, SettingsEnums.SETTINGS_HOMEPAGE);

        getLifecycle().addObserver(new HideNonSystemOverlayMixin(this));

	toolbarAvatar = root.findViewById(R.id.toolbar_avatar);
        toolbarAvatar.setImageDrawable(getCircularUserIcon(context));
        toolbarAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
	        intent.setComponent(new ComponentName("com.android.settings",
                        "com.android.settings.Settings$UserSettingsActivity"));
                startActivity(intent);
            }
        });


        showFragment(new TopLevelSettings(), R.id.main_content);
        ((FrameLayout) findViewById(R.id.main_content))
                .getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);

        homepageMainLayout = findViewById(R.id.main_content_scrollable_container);

	if (homepageMainLayout != null) {
            setMargins(homepageMainLayout, 0,0,0,0);
        }

	appBarLayout = findViewById(R.id.app_bar_layout);
	appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                float offsetAlpha = (Float.valueOf(appBarLayout.getTotalScrollRange() + verticalOffset) / Float.valueOf(appBarLayout.getTotalScrollRange()));
                toolbarAvatar.setAlpha(offsetAlpha);
                if (Math.abs(verticalOffset)-appBarLayout.getTotalScrollRange() == 0){
                    toolbarAvatar.setVisibility(View.GONE);
                } else {
                    toolbarAvatar.setVisibility(View.VISIBLE);
                }
            }
        });

    }

    private void showFragment(Fragment fragment, int id) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        final Fragment showFragment = fragmentManager.findFragmentById(id);

        if (showFragment == null) {
            fragmentTransaction.add(id, fragment);
        } else {
            fragmentTransaction.show(showFragment);
        }
        fragmentTransaction.commit();
    }

    @VisibleForTesting
    void setHomepageContainerPaddingTop() {
        final View view = this.findViewById(R.id.homepage_container);

        final int searchBarHeight = getResources().getDimensionPixelSize(R.dimen.search_bar_height);
        final int searchBarMargin = getResources().getDimensionPixelSize(R.dimen.search_bar_margin);

        // The top padding is the height of action bar(48dp) + top/bottom margins(16dp)
        final int paddingTop = searchBarHeight + searchBarMargin * 2;
        view.setPadding(0 /* left */, paddingTop, 0 /* right */, 0 /* bottom */);

        // Prevent inner RecyclerView gets focus and invokes scrolling.
        view.setFocusableInTouchMode(true);
        view.requestFocus();
    }

    private Drawable getCircularUserIcon(Context context) {
        Bitmap bitmapUserIcon = mUserManager.getUserIcon(UserHandle.myUserId());

        if (bitmapUserIcon == null) {
            // get default user icon.
            final Drawable defaultUserIcon = UserIcons.getDefaultUserIcon(
                    context.getResources(), UserHandle.myUserId(), false);
            bitmapUserIcon = UserIcons.convertToBitmap(defaultUserIcon);
        }
        Drawable drawableUserIcon = new CircleFramedDrawable(bitmapUserIcon,
                (int) context.getResources().getDimension(R.dimen.circle_avatar_size));

        return drawableUserIcon;
    }

    @Override
    public void onResume() {
        super.onResume();
	goodVibesPlease();
    }

    private boolean isHomepageSpacerEnabled() {
        return true;
    }

    private static void setMargins (View v, int l, int t, int r, int b) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.setMargins(l, t, r, b);
            v.requestLayout();
        }
    }

    private void getRandomName(){
    Resources res = getResources();
    String[] array = res.getStringArray(R.array.random_user_names);
    String randomName = array[new Random().nextInt(array.length)];
    TextView homepageUsernameTextView=(TextView) findViewById(R.id.userNameTextView);
    homepageUsernameTextView.setText(randomName);
    }

    private void goodVibesPlease(){

     Calendar c = Calendar.getInstance();
     int hours = c.get(Calendar.HOUR_OF_DAY);
     String greeting=null;
     TextView homePageGreetingTextView=(TextView) findViewById(R.id.greetingsTextView);
     View root = findViewById(R.id.settings_homepage_container);

     if(hours>=0 && hours<=11){
         greeting = "Good Morning!!";

     } else if(hours>=12 && hours<=15){

         greeting = "Good AfterNoon!!";

     } else if(hours>=16 && hours<=20){

         greeting = "Good Evening!!";

     } else if(hours>=21 && hours<=24){

         greeting = "Good Night!!";

     } else {
         greeting = "Stay Colt-ify";
     }

     homePageGreetingTextView.setText(greeting);

 }
}
