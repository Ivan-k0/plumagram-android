/*
 * This file is a part of TGx101, a modification of Telegram X
 * Copyright © 2026 1vank0 (https://github.com/Ivan-k0/plumagram-android)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.AvatarPickerManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.AvatarView;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import tgx.td.Td;

/**
 * TGx101 (approved mockup «Профиль и Настройки» Я1, user 2026-10-26): my own profile — the photo in a circle on the
 * header's colour, name and status, three buttons (choose photo, edit, settings), the personal channel and my details
 * on cards. Opened from the profile card at the top of Settings.
 */
public class Tgx101MyProfileController extends ViewController<Void> implements TdlibCache.MyUserDataChangeListener, TdlibCache.UserDataChangeListener, org.thunderdog.challegram.navigation.ActivityResultHandler {
  public Tgx101MyProfileController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    avatarPicker = new AvatarPickerManager(this);
  }

  private final AvatarPickerManager avatarPicker;
  private AvatarView avatar;
  private TextView name, status;
  private LinearLayout cards;

  @Override
  public int getId () {
    return R.id.controller_tgx101MyProfile;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Tgx101MyProfile);
  }

  @Override
  protected View onCreateView (Context context) {
    ScrollView scroll = new ScrollView(context);
    scroll.setFillViewport(true);
    ViewSupportHelper.background(scroll, ColorId.background, this);
    LinearLayout root = new LinearLayout(context);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(0, 0, 0, Screen.dp(16f) + MainController.tgx101CapsuleSpace());
    scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // the header block: the header's own colour, so it runs on from the bar above without a seam
    LinearLayout head = new LinearLayout(context);
    head.setOrientation(LinearLayout.VERTICAL);
    head.setGravity(Gravity.CENTER_HORIZONTAL);
    head.setPadding(Screen.dp(12f), Screen.dp(4f), Screen.dp(12f), Screen.dp(14f));
    ViewSupportHelper.background(head, ColorId.headerBackground, this);
    avatar = new AvatarView(context);
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(Screen.dp(88f), Screen.dp(88f));
    head.addView(avatar, ap);
    // (user 2026-10-26 «в профиле фото не открывается»): a tap opens the photo; changing it is the «Choose photo» button
    avatar.setOnClickListener(v -> {
      TdApi.User me = tdlib.myUser();
      if (me != null && me.profilePhoto != null) {
        org.thunderdog.challegram.mediaview.MediaViewController.openFromProfile(this, me, null);
      } else {
        avatarPicker.showMenuForProfile(null, false);
      }
    });
    name = new TextView(context);
    name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f);
    name.setTypeface(org.thunderdog.challegram.tool.Fonts.getRobotoMedium());
    name.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    np.topMargin = Screen.dp(10f);
    head.addView(name, np);
    status = new TextView(context);
    status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    status.setGravity(Gravity.CENTER);
    head.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    bp.topMargin = Screen.dp(14f);
    head.addView(buttons, bp);
    addButton(buttons, R.drawable.baseline_camera_alt_24, R.string.Tgx101ChoosePhoto, v -> avatarPicker.showMenuForProfile(null, false));
    addButton(buttons, R.drawable.baseline_edit_24, R.string.Tgx101EditProfile, v -> navigateTo(new Tgx101AccountController(context, tdlib)));
    addButton(buttons, R.drawable.baseline_settings_24, R.string.Settings, v -> {
      ViewController<?> prev = previousStackItem();
      if (prev instanceof SettingsController) navigateBack();
      else navigateTo(new SettingsController(context, tdlib));
    });
    root.addView(head);

    cards = new LinearLayout(context);
    cards.setOrientation(LinearLayout.VERTICAL);
    cards.setPadding(Screen.dp(12f), Screen.dp(4f), Screen.dp(12f), 0);
    root.addView(cards);

    bind();
    tdlib.cache().addMyUserListener(this);
    tdlib.cache().addUserDataListener(tdlib.myUserId(), this);
    return scroll;
  }

  /** a small helper so the background follows the theme */
  private static final class ViewSupportHelper {
    static void background (View view, int colorId, ViewController<?> themeProvider) {
      org.thunderdog.challegram.support.ViewSupport.setThemedBackground(view, colorId, themeProvider);
    }
  }

  private void addButton (LinearLayout row, int icon, int text, View.OnClickListener onClick) {
    LinearLayout b = new LinearLayout(context);
    b.setOrientation(LinearLayout.VERTICAL);
    b.setGravity(Gravity.CENTER);
    b.setPadding(0, Screen.dp(9f), 0, Screen.dp(8f));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0x2effffff);
    bg.setCornerRadius(Screen.dp(14f));
    b.setBackground(bg);
    ImageView iv = new ImageView(context);
    iv.setImageResource(icon);
    iv.setColorFilter(0xffffffff);
    b.addView(iv, new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
    TextView t = new TextView(context);
    t.setText(Lang.getString(text));
    t.setTextColor(0xffffffff);
    t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
    t.setSingleLine(true);
    t.setGravity(Gravity.CENTER);
    b.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    b.setOnClickListener(onClick);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    lp.leftMargin = lp.rightMargin = Screen.dp(4f);
    row.addView(b, lp);
  }

  private LinearLayout card () {
    LinearLayout c = new LinearLayout(context);
    c.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Theme.fillingColor());
    bg.setCornerRadius(Screen.dp(14f));
    c.setBackground(bg);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = Screen.dp(10f);
    cards.addView(c, lp);
    return c;
  }

  private void row (LinearLayout card, CharSequence value, String label, @Nullable View.OnClickListener onClick) {
    if (card.getChildCount() > 0) {
      View line = new View(context);
      line.setBackgroundColor(Theme.separatorColor());
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Screen.dp(.5f)));
      lp.leftMargin = Screen.dp(16f);
      card.addView(line, lp);
    }
    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.VERTICAL);
    r.setPadding(Screen.dp(16f), Screen.dp(10f), Screen.dp(16f), Screen.dp(10f));
    TextView v = new TextView(context);
    v.setText(value);
    v.setTextColor(Theme.textAccentColor());
    v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    r.addView(v);
    TextView l = new TextView(context);
    l.setText(label);
    l.setTextColor(Theme.textDecentColor());
    l.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    r.addView(l);
    if (onClick != null) {
      r.setOnClickListener(onClick);
      org.thunderdog.challegram.tool.Views.setClickable(r);
    }
    card.addView(r);
  }

  private void bind () {
    if (avatar == null) return;
    TdApi.User me = tdlib.myUser();
    TdApi.UserFullInfo full = tdlib.myUserFull();
    avatar.setUser(tdlib, me, false);
    name.setText(me != null ? TD.getUserName(me) : "");
    name.setTextColor(Theme.getColor(ColorId.headerText));
    status.setText(Lang.getString(R.string.status_Online));
    status.setTextColor(ColorUtils.alphaColor(.7f, Theme.getColor(ColorId.headerText)));
    cards.removeAllViews();
    View.OnClickListener edit = v -> navigateTo(new Tgx101AccountController(context, tdlib));
    if (full != null && full.personalChatId != 0) {
      TdApi.Chat ch = tdlib.chat(full.personalChatId);
      if (ch != null) {
        LinearLayout c = card();
        row(c, ch.title, Lang.getString(R.string.Tgx101PersonalChannel), v -> tdlib.ui().openChat(this, ch.id, null));
      }
    }
    LinearLayout info = card();
    if (me != null && !StringUtils.isEmpty(me.phoneNumber)) row(info, Strings.formatPhone(me.phoneNumber), Lang.getString(R.string.Phone), edit);
    if (full != null && full.bio != null && !StringUtils.isEmpty(full.bio.text)) row(info, full.bio.text, Lang.getString(R.string.UserBio), edit);
    if (me != null && Td.hasUsername(me)) row(info, "@" + Td.primaryUsername(me), Lang.getString(R.string.Username), v -> {
      UI.copyText(tdlib.tMeUrl(Td.primaryUsername(me)), R.string.CopiedLink);
    });
    if (me != null) row(info, Long.toString(me.id), "ID", v -> UI.copyText(Long.toString(me.id), R.string.CopiedMyUserId));
    if (full != null && full.birthdate != null) row(info, Lang.getBirthdate(full.birthdate, true, true), Lang.getString(R.string.Birthdate), edit);
  }

  @Override
  public void onMyUserUpdated (TdApi.User myUser) {
    if (myUser != null) runOnUiThreadOptional(this::bind);
  }

  @Override
  public void onMyUserBioUpdated (@Nullable TdApi.FormattedText newBio) {
    runOnUiThreadOptional(this::bind);
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    runOnUiThreadOptional(this::bind);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    bind();
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    avatarPicker.handleActivityResult(requestCode, resultCode, data, AvatarPickerManager.MODE_PROFILE, null, null);
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().removeMyUserListener(this);
    tdlib.cache().removeUserDataListener(tdlib.myUserId(), this);
    if (avatar != null) avatar.performDestroy();
  }
}
