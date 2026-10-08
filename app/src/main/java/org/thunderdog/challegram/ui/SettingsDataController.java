/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 16/11/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGNetworkStats;
import org.thunderdog.challegram.data.TGStorageStatsFast;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.GlobalConnectionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.voip.annotation.DataSavingOption;

import java.util.List;

import me.vkryl.core.ArrayUtils;

public class SettingsDataController extends RecyclerViewController<SettingsDataController.Args> implements View.OnClickListener, ViewController.SettingsIntDelegate,
  GlobalConnectionListener, Client.ResultHandler, Settings.ProxyChangeListener {
  public static class Args {
    public int mode;
    public Object data;
    /** TGx101 (user 2026-10-05): MagiX split into sections; -1 — the list of sections */
    public int tgx101Section = -1;

    public Args setTgx101Section (int section) {
      this.tgx101Section = section;
      return this;
    }

    public Args (int mode) {
      this.mode = mode;
    }

    public Args setData (Object data) {
      this.data = data;
      return this;
    }
  }

  public SettingsDataController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    this.tgx101Section = args.tgx101Section;
    switch (mode) {
      case MODE_STATISTICS: {
        networkStats = (TGNetworkStats) args.data;
        break;
      }
    }
  }

  private int mode;
  private int tgx101Section = -1;

  private static final int MODE_NONE = 0;
  private static final int MODE_STATISTICS = 1;
  /** TGx101: all of the mod's own settings, opened from the main settings list. */
  public static final int MODE_TGX101 = 2;

  @Override
  public int getId () {
    return R.id.controller_chatSettings;
  }

  @Override
  public CharSequence getName () {
    if (mode == MODE_TGX101 && tgx101Section >= 0 && tgx101Section < TGX101_SECTION_TITLES.length) {
      return Lang.getString(TGX101_SECTION_TITLES[tgx101Section]);
    }
    return Lang.getString(mode == MODE_STATISTICS ? R.string.NetworkUsage : mode == MODE_TGX101 ? R.string.Tgx101Settings : R.string.DataSettings);
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putInt(keyPrefix + "mode", mode);
    outState.putInt(keyPrefix + "tgx101Section", tgx101Section);
    if (mode == MODE_NONE) {
      outState.putBoolean(keyPrefix + "advanced", adapter.indexOfViewById(R.id.btn_showAdvanced) == -1);
    }
    return true;
  }

  private boolean forceOpenAdvanced;

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    int mode = in.getInt(keyPrefix + "mode", MODE_NONE);
    forceOpenAdvanced = mode == MODE_NONE && in.getBoolean(keyPrefix + "advanced", false);
    if (mode != MODE_NONE) {
      setArguments(new Args(mode).setTgx101Section(in.getInt(keyPrefix + "tgx101Section", -1)));
    }
    return true;
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  private SettingsAdapter adapter;

  private View headerCell;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @SuppressLint("InflateParams")
  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    org.thunderdog.challegram.data.Tgx101SpeechModels.addListener(tgx101SpeechListener);
    this.adapter = new SettingsAdapter(this) {
      @Override
      public void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (mode == MODE_TGX101) {
          // TGx101: hold to read a title cut with "…"; tap only toggles
          view.setOnLongClickListener(v -> {
            if (((SettingView) v).isNameEllipsized()) {
              context().tooltipManager().builder(v).show(tdlib, ((SettingView) v).getName().toString()).hideDelayed();
              return true;
            }
            return false;
          });
        }
        int tgx101SectionIndex = tgx101SectionIndex(itemId);
        if (tgx101SectionIndex != -1) {
          view.setData(Lang.getString(TGX101_SECTION_HINTS[tgx101SectionIndex]));
          return;
        }
        if (itemId == R.id.btn_dataSaver) {
          final boolean isEnabled = !tdlib.files().isDataSaverEventuallyEnabled();
          if (isUpdate) {
            view.setEnabledAnimated(isEnabled);
          } else {
            view.setEnabled(isEnabled);
          }
          view.getToggler().setRadioEnabled(tdlib.files().isDataSaverActive(), isUpdate);
        } else if (itemId == R.id.btn_dataSaverForce) {
          final boolean overMobile = tdlib.files().isDataSaverEnabledOverMobile();
          final boolean overRoaming = tdlib.files().isDataSaverEnabledOverRoaming();
          final int resource = overMobile && overRoaming ? R.string.WhenUsingMobileDataOrRoaming : overMobile ? R.string.WhenUsingMobileData : overRoaming ? R.string.WhenUsingRoaming : R.string.Never;
          view.setData(resource);

          final boolean isEnabled = tdlib.files().isDataSaverEventuallyEnabled() || !tdlib.files().isDataSaverAlwaysEnabled();
          if (isUpdate) {
            view.setEnabledAnimated(isEnabled);
          } else {
            view.setEnabled(isEnabled);
          }
        } else if (itemId == R.id.btn_proxy) {
          int proxyId = Settings.instance().getEffectiveProxyId();
          if (proxyId != Settings.PROXY_ID_NONE) {
            view.setData(Settings.instance().getProxyName(proxyId));
          } else {
            view.setData(Settings.instance().getAvailableProxyCount() == 0 ? R.string.ProxySetup : R.string.ProxyDisabled);
          }
        } else if (itemId == R.id.btn_inPrivateChats) {
          view.setData(tdlib.files().getDownloadInPrivateChatsList());
        } else if (itemId == R.id.btn_inGroupChats) {
          view.setData(tdlib.files().getDownloadInGroupChatsList());
        } else if (itemId == R.id.btn_inChannelChats) {
          view.setData(tdlib.files().getDownloadInChannelChatsList());
        } else if (itemId == R.id.btn_mediaWiFiLimits) {
          view.setData(tdlib.files().getDownloadLimitOverWiFiString());
        } else if (itemId == R.id.btn_mediaMobileLimits) {
          view.setData(tdlib.files().getDownloadLimitOverMobileString());
        } else if (itemId == R.id.btn_mediaRoamingLimits) {
          view.setData(tdlib.files().getDownloadLimitOverRoamingString());
          // Voice
        } else if (itemId == R.id.btn_tgx101RingRampTime) {
          view.setData(Lang.getString(R.string.Tgx101RingRampSeconds, Settings.instance().getTgx101RingRampSeconds()));
        } else if (itemId == R.id.btn_lessDataForCalls) {
          switch (tdlib.files().getVoipDataSavingOption()) {
            case DataSavingOption.ALWAYS:
              view.setData(R.string.UseLessDataAlways);
              break;
            case DataSavingOption.MOBILE:
              view.setData(R.string.OnMobileNetwork);
              break;
            case DataSavingOption.ROAMING:
              view.setData(R.string.OnRoaming);
              break;
            case DataSavingOption.NEVER:
            default:
              view.setData(R.string.Never);
              break;
          }
          // Storage usage
        } else if (itemId == R.id.btn_storageUsage) {
          view.setData(storageStats != null ? storageStats.isEmpty() ? Lang.getString(R.string.StorageUsageHint) : storageStats.getTotalSizeEntry() : Lang.getString(R.string.Calculating));
          // Data usage
        } else if (itemId == R.id.btn_dataUsageTotal) {
          view.setData(networkStats != null ? networkStats.getTotalEntry() : Lang.getString(R.string.Calculating));
        } else if (itemId == R.id.btn_dataUsageMobile) {
          view.setData(networkStats != null ? networkStats.getMobileEntry() : Lang.getString(R.string.Calculating));
        } else if (itemId == R.id.btn_dataUsageRoaming) {
          view.setData(networkStats != null ? networkStats.getRoamingEntry() : Lang.getString(R.string.Calculating));
        } else if (itemId == R.id.btn_dataUsageWiFi) {
          view.setData(networkStats != null ? networkStats.getWiFiEntry() : Lang.getString(R.string.Calculating));
        } else if (itemId == R.id.btn_resetNetworkStats) {
          view.setData(networkStats != null ? networkStats.getDateEntry() : Lang.getString(R.string.LoadingInformation));
        } else if (itemId == R.id.btn_tgx101CallPhoto) {
          view.setData(callPhotoModeName(Settings.instance().getCallPhotoMode()));
        } else if (itemId == R.id.btn_tgx101CallPattern) {
          view.getToggler().setRadioEnabled(Settings.instance().getCallPattern() != Settings.CALL_PATTERN_NONE, isUpdate);
        } else if (itemId == R.id.btn_tgx101RingRamp) {
          view.getToggler().setRadioEnabled(Settings.instance().isRingRampEnabled(), isUpdate);
        } else if (itemId == R.id.btn_tgx101CameraInAttach) {
          view.getToggler().setRadioEnabled(Settings.instance().isCameraInAttach(), isUpdate);
        } else if (itemId == R.id.btn_tgx101StoriesFolders) {
          view.setData(tgx101StoriesFoldersValue());
        } else if (itemId == R.id.btn_tgx101StoriesMode) {
          view.setData(Tgx101Stories.mode() == Tgx101Stories.MODE_RINGS ? R.string.Tgx101StoriesModeRings : R.string.Tgx101StoriesModeStrip);
        } else if (itemId == R.id.btn_tgx101NextChannelSwipe) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101NextChannelSwipe(), isUpdate);
        } else if (itemId == R.id.btn_tgx101HideAllReactions) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101HideAllReactions(), isUpdate);
        } else if (itemId == R.id.btn_tgx101HideInputCamera) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101HideInputCamera(), isUpdate);
        } else if (itemId == R.id.btn_tgx101HideInputCommands) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101HideInputCommands(), isUpdate);
        } else if (itemId == R.id.btn_tgx101CapsuleMenu) {
          view.setData(tgx101MaskValue(Settings.instance().tgx101CapsuleMenu(), MainController.TGX101_CAPSULE_MENU_TITLES));
        } else if (itemId == R.id.btn_tgx101ContactsMenu) {
          view.setData(tgx101MaskValue(Settings.instance().tgx101ContactsMenu(), MainController.TGX101_CONTACTS_MENU_TITLES));
        } else if (itemId == R.id.btn_tgx101NavCapsule) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101NavCapsule(), isUpdate);
        } else if (itemId == R.id.btn_tgx101HideCompose) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101HideCompose(), isUpdate);
        } else if (itemId == R.id.btn_tgx101CapsuleScrollHide) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101CapsuleScrollHide(), isUpdate);
        } else if (itemId == R.id.btn_tgx101HideInputEmoji) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101HideInputEmoji(), isUpdate);
        } else if (itemId == R.id.btn_tgx101RearRounds) {
          view.getToggler().setRadioEnabled(Settings.instance().startRoundWithRear(), isUpdate);
        } else if (itemId == R.id.btn_tgx101HidePhone) {
          view.getToggler().setRadioEnabled(Settings.instance().needHidePhoneNumber(), isUpdate);
        } else if (itemId == R.id.btn_tgx101HideChannelReactions) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101HideChannelReactions(), isUpdate);
        } else if (itemId == R.id.btn_tgx101ZoomPullClose) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101ZoomPullClose(), isUpdate);
        } else if (itemId == R.id.btn_tgx101SwipeActions) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101SwipeActions(), isUpdate);
        } else if (itemId == R.id.btn_tgx101LongPressMenu) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101LongPressMenu(), isUpdate);
        } else if (itemId == R.id.btn_tgx101MenuHidesKeyboard) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101MenuHidesKeyboard(), isUpdate);
        } else if (itemId == R.id.btn_tgx101Haptics) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101Haptics(), isUpdate);
        } else if (itemId == R.id.btn_tgx101MenuAtFinger) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101MenuAtFinger(), isUpdate);
        } else if (itemId == R.id.btn_tgx101UiText) {
          view.setData(Settings.instance().tgx101UiTextSize() + "%");
        } else if (itemId == R.id.btn_tgx101UiBold) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101UiBold(), isUpdate);
        } else if (itemId == R.id.btn_tgx101ForegroundNotif) {
          view.setData(FOREGROUND_NOTIF_NAMES[Math.max(0, Math.min(2, Settings.instance().tgx101ForegroundNotifMode()))]);
        } else if (itemId == R.id.btn_tgx101Snooze) {
          int minutes = Settings.instance().tgx101SnoozeMinutes();
          view.setData(minutes > 0 ? Lang.getString(R.string.Tgx101SnoozeMinutes, minutes) : Lang.getString(R.string.Tgx101SnoozeOff));
        } else if (itemId == R.id.btn_tgx101VoiceQueue) {
          view.setData(Settings.instance().skipOwnVoiceInQueue() ? R.string.Tgx101VoiceQueueSkipOwn : R.string.Tgx101VoiceQueueAll);
        } else if (itemId == R.id.btn_tgx101TapMode) {
          view.setData(tapModeName(Settings.instance().getTapMode()));
        } else if (itemId == R.id.btn_tgx101NewCallScreen) {
          view.getToggler().setRadioEnabled(Settings.instance().useNewCallScreen(), isUpdate);
        } else if (quickReplyIndex(itemId) != -1) {
          view.setData(Settings.instance().getQuickReply(quickReplyIndex(itemId)));
        } else if (itemId == R.id.btn_tgx101TranslateOnDevice) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101TranslateOnDevice(), isUpdate);
        } else if (itemId == R.id.btn_tgx101SpeechModel) {
          view.setData(tgx101SpeechModelStatus());
        } else if (itemId == R.id.btn_tgx101FakeNoPremium) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101FakeNoPremium(), isUpdate);
        } else if (itemId == R.id.btn_showChannelMuteButton) {
          view.getToggler().setRadioEnabled(Settings.instance().showChannelMuteButton(), isUpdate);
        } else if (itemId == R.id.btn_showDiscussButton) {
          view.getToggler().setRadioEnabled(Settings.instance().showDiscussButton(), isUpdate);
        } else if (itemId == R.id.btn_showCommentsButton) {
          view.getToggler().setRadioEnabled(Settings.instance().showCommentsButton(), isUpdate);
        } else if (itemId == R.id.btn_hideSubscribeLink) {
          view.getToggler().setRadioEnabled(Settings.instance().hideChannelSubscribeLink(), isUpdate);
        } else if (itemId == R.id.btn_tgx101CheckUpdates) {
          view.getToggler().setRadioEnabled(org.thunderdog.challegram.service.Tgx101Updates.isEnabled(context()), isUpdate);
        } else if (itemId == R.id.btn_separateChannelPosts) {
          view.getToggler().setRadioEnabled(Settings.instance().separateChannelPosts(), isUpdate);
        } else if (itemId == R.id.btn_tgx101FloatingInput) {
          view.getToggler().setRadioEnabled(Settings.instance().useFloatingInput(), isUpdate);
        } else if (itemId == R.id.btn_tgx101BottomGap) {
          view.getToggler().setRadioEnabled(Settings.instance().bottomGapEnabled(), isUpdate);
        } else if (itemId == R.id.btn_tgx101NotificationPlane) {
          view.getToggler().setRadioEnabled(Settings.instance().useTgx101NotificationPlane(), isUpdate);
        } else if (itemId == R.id.btn_tgx101HideBlocked) {
          view.getToggler().setRadioEnabled(Settings.instance().tgx101HideBlocked(), isUpdate);
        } else if (itemId == R.id.btn_tgx101Filters) {
          int count = org.thunderdog.challegram.data.Tgx101MessageFilters.getRules().size();
          view.setData(count == 0 || !org.thunderdog.challegram.data.Tgx101MessageFilters.isEnabled() ? Lang.getString(R.string.Tgx101FiltersOff) : Integer.toString(count));
        } else if (itemId == R.id.btn_pullToSearch) {
          view.getToggler().setRadioEnabled(Settings.instance().isPullToSearchEnabled(), isUpdate);
        } else if (itemId == R.id.btn_roundStabilization) {
          view.setData(roundStabilizationName(Settings.instance().getRoundStabilizationMode()));
        } else if (itemId == R.id.btn_roundVideoQuality) {
          view.setData(roundVideoQualityName(Settings.instance().getRoundVideoQuality()));
        } else if (itemId == R.id.btn_bigEmojiSize) {
          view.setData(bigEmojiSizeName(Settings.instance().getBigEmojiSize()));
        } else if (itemId == R.id.btn_tgx101MessageMenu) {
          view.getToggler().setRadioEnabled(Settings.instance().useTgx101MessageMenu(), isUpdate);
        } else if (itemId == R.id.btn_tgx101TextEditor) {
          view.getToggler().setRadioEnabled(Settings.instance().useTgx101TextEditor(), isUpdate);
        } else if (itemId == R.id.btn_tgx101CallBar) {
          view.getToggler().setRadioEnabled(Settings.instance().showTgx101CallBar(), isUpdate);
        } else if (itemId == R.id.btn_tgx101MessageMenuHand) {
          view.setData(Settings.instance().isTgx101MessageMenuLeftHand() ? R.string.Tgx101MessageMenuHandLeft : R.string.Tgx101MessageMenuHandRight);
        } else if (itemId == R.id.btn_tgx101ChatListTextSize) {
          view.setData(chatListTextSizeName(Settings.instance().getChatListTextSize()));
        } else if (itemId == R.id.btn_tgx101TextWeight) {
          view.setData(textWeightName(Settings.instance().getTextWeight()));
        } else if (itemId == R.id.btn_tgx101Font) {
          int appFont = Settings.instance().getAppFont();
          view.setData(appFont == Settings.APP_FONT_SYSTEM ? R.string.Tgx101FontSystem : appFont == Settings.APP_FONT_ROBOTO ? R.string.Tgx101FontRoboto : R.string.Tgx101FontManrope);
        } else if (itemId == R.id.btn_toggleNewSetting) {
          updateSettingView(view, item, isUpdate);
        }
      }
    };

    final ListItem[] rawItems;

    if (mode == MODE_STATISTICS) {
      if (tdlib.context().isMultiUser()) {
        DoubleHeaderView headerCell = new DoubleHeaderView(context);
        headerCell.setThemedTextColor(this);
        headerCell.initWithMargin(0, true);
        headerCell.setTitle(getName());
        headerCell.setSubtitle(tdlib.account().getName());
        this.headerCell = headerCell;
      }
      rawItems = new ListItem[] {
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataUsageMobile, R.drawable.baseline_signal_cellular_alt_24, R.string.MobileUsage),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataUsageWiFi, R.drawable.baseline_wifi_24, R.string.WiFiUsage),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataUsageRoaming, R.drawable.baseline_public_24, R.string.RoamingUsage),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_resetNetworkStats, 0, R.string.ResetStatistics).setTextColorId(ColorId.textNegative),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
      };
    } else if (mode == MODE_TGX101) {
      rawItems = new ListItem[] {
        new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL),
        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.RecordingAndPhotos),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.PauseMediaOnRecord).setLongId(Settings.SETTING_FLAG_PAUSE_MEDIA_ON_RECORD),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101VoiceQueue, 0, R.string.Tgx101VoiceQueue),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101CameraInAttach, 0, R.string.Tgx101CameraInAttach),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101ZoomPullClose, 0, R.string.Tgx101ZoomPullClose),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101Snooze, 0, R.string.Tgx101SnoozeSetting),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.SendPhotosInHD).setLongId(Settings.SETTING_FLAG_SEND_PHOTOS_IN_HD),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_roundVideoQuality, 0, R.string.RoundVideoQuality),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_roundStabilization, 0, R.string.RoundStabilization),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        // TGx101: text size and weight
        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101TextSection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101UiText, 0, R.string.Tgx101UiText),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101UiBold, 0, R.string.Tgx101UiBold),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101ChatListTextSize, 0, R.string.Tgx101ChatListTextSize),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101Font, 0, R.string.Tgx101Font),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101TextWeight, 0, R.string.Tgx101TextWeight),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_SETTING, R.id.btn_chatFontSize, 0, R.string.TextSize),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        // TGx101: compact message menu
        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101MessageMenuSection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101MessageMenu, 0, R.string.Tgx101MessageMenu),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101TextEditor, 0, R.string.Tgx101TextEditorSetting),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101CallBar, 0, R.string.Tgx101CallBarSetting),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101MessageMenuHand, 0, R.string.Tgx101MessageMenuHand),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101MenuAtFinger, 0, R.string.Tgx101MenuAtFinger),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101LongPressMenu, 0, R.string.Tgx101LongPressMenu),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101SwipeActions, 0, R.string.Tgx101SwipeActions),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101Haptics, 0, R.string.Tgx101Haptics),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101MenuHidesKeyboard, 0, R.string.Tgx101MenuHidesKeyboard),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101MessageMenuOrder, 0, R.string.Tgx101MessageMenuOrder),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101TapMode, 0, R.string.Tgx101TapMode),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101TapModeHint),

        // TGx101: the input's own section, so the mini editor's buttons are easy to find
        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101InputSection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101FormatMenu, 0, R.string.Tgx101FormatMenu),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101InputSectionHint),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChatListSection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_bigEmojiSize, 0, R.string.BigEmojiSize),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101Filters, 0, R.string.Tgx101Filters),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101HideBlocked, 0, R.string.Tgx101HideBlocked),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101StoriesMode, 0, R.string.Tgx101StoriesMode),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101StoriesFolders, 0, R.string.Tgx101StoriesFolders),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101HideChannelReactions, 0, R.string.Tgx101HideChannelReactions),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101HideAllReactions, 0, R.string.Tgx101HideAllReactions),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101NextChannelSwipe, 0, R.string.Tgx101NextChannelSwipe),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_pullToSearch, 0, R.string.PullToSearch),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FloatingInput, 0, R.string.Tgx101FloatingInput),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101NavCapsule, 0, R.string.Tgx101NavCapsule),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),

        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101CapsuleMenu, 0, R.string.Tgx101CapsuleMenu),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101ContactsMenu, 0, R.string.Tgx101ContactsMenu),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101QuickCalls, 0, R.string.Tgx101QuickCalls),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101HideInputCamera, 0, R.string.Tgx101HideInputCamera),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101HideInputCommands, 0, R.string.Tgx101HideInputCommands),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101HideInputEmoji, 0, R.string.Tgx101HideInputEmoji),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101RearRounds, 0, R.string.Tgx101RearRounds),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101HidePhone, 0, R.string.Tgx101HidePhone),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101BottomGap, 0, R.string.Tgx101BottomGap),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101NotificationPlane, 0, R.string.Tgx101NotificationPlane),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101ForegroundNotif, 0, R.string.Tgx101ForegroundNotif),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PullToSearchHint),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101TranslateSection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101TranslateOnDevice, 0, R.string.Tgx101TranslateOnDevice),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgx101TranslateModels, 0, R.string.Tgx101TranslateModels),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101TranslateOnDeviceHint),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101SpeechSection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101SpeechModel, 0, R.string.Tgx101SpeechModel),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101SpeechModelHint),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.ChannelsSection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_showDiscussButton, 0, R.string.ShowDiscussButton),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_showCommentsButton, 0, R.string.ShowCommentsButton),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_showChannelMuteButton, 0, R.string.ShowChannelMuteButton),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideSubscribeLink, 0, R.string.HideSubscribeLink),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_separateChannelPosts, 0, R.string.SeparateChannelPosts),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ChannelButtonsHint),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101CallsSection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101NewCallScreen, 0, R.string.Tgx101NewCallScreen),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101CallPhoto, 0, R.string.Tgx101CallPhoto),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101CallPattern, 0, R.string.Tgx101CallPattern),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101RingRamp, 0, R.string.Tgx101RingRamp),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgx101RingRampTime, 0, R.string.Tgx101RingRampTime),

        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101CallPatternHint),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101QuickReplies),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101QuickReply1, 0, Lang.getString(R.string.Tgx101QuickReplyN, 1), false),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101QuickReply2, 0, Lang.getString(R.string.Tgx101QuickReplyN, 2), false),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101QuickReply3, 0, Lang.getString(R.string.Tgx101QuickReplyN, 3), false),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101QuickReply4, 0, Lang.getString(R.string.Tgx101QuickReplyN, 4), false),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_tgx101QuickReply5, 0, Lang.getString(R.string.Tgx101QuickReplyN, 5), false),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101QuickRepliesHint),

        // TGx101: the update check moved to Settings → Interface, in place of Telegram X's in-app updates

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Contacts),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.WriteContactsToPhoneBook).setLongId(Settings.SETTING_FLAG_WRITE_CONTACTS_TO_PHONEBOOK),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BackgroundConnection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_toggleNewSetting, 0, R.string.KeepAliveConnectionSetting).setLongId(Settings.SETTING_FLAG_KEEP_ALIVE_CONNECTION),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.KeepAliveConnectionHint), // after the block: shown in the section
      };
    } else {
      rawItems = new ListItem[] {
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_storageUsage, R.drawable.baseline_data_usage_24, R.string.StorageUsage),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataUsageTotal, R.drawable.baseline_import_export_24, R.string.NetworkUsage),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_dataSaver, 0, R.string.DataSaver),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataSaverForce, 0, R.string.TurnOnAutomatically),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DataSaverDesc),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Connection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_lessDataForCalls, 0, R.string.VoipUseLessData),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_proxy, 0, R.string.Proxy),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AutomaticMediaDownload),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inPrivateChats, 0, R.string.InPrivateChats),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inGroupChats, 0, R.string.InGroups),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inChannelChats, 0, R.string.InChannels),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, 0, R.string.Advanced),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

      };
    }
    ListItem[] shown = rawItems;
    if (true /* user 2026-10-08: all test changes go to the release */) {
      shown = tgx101TestItems(rawItems);
    }
    this.adapter.setItems(shown, false);
    if (org.thunderdog.challegram.BuildConfig.TGX101_DIAG) {
      // TGx101: test switches, diagnostics builds only — own section above «Translation»
      int at = adapter.indexOfViewById(R.id.btn_tgx101TranslateOnDevice);
      if (at >= 2) {
        at -= 2; // before the section's header and top shadow
        List<ListItem> items = adapter.getItems();
        items.add(at++, new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Tgx101TestSection));
        items.add(at++, new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(at++, new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101FakeNoPremium, 0, R.string.Tgx101FakeNoPremium));
        items.add(at++, new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.Tgx101FakeNoPremiumHint));
        items.add(at, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        adapter.notifyDataSetChanged();
      }
    }
    if (mode == MODE_TGX101) {
      tgx101ApplySection();
    }
    if (forceOpenAdvanced) {
      List<ListItem> items = adapter.getItems();
      int index = adapter.indexOfViewById(R.id.btn_showAdvanced);
      if (index != -1) {
        items.remove(index);
        items.remove(index);

        ListItem[] advancedItems = newAdvancedItems();

        ArrayUtils.ensureCapacity(items, items.size() + advancedItems.length);
        int i = index;
        for (ListItem item : advancedItems) {
          items.add(i++, item);
        }
      }
    }
    recyclerView.setAdapter(adapter);

    switch (mode) {
      case MODE_NONE: {
        tdlib.client().send(new TdApi.GetStorageStatisticsFast(), this);
        tdlib.client().send(new TdApi.GetNetworkStatistics(), this);

        TdlibManager.instance().global().addConnectionListener(this);
        Settings.instance().addProxyListener(this);
        break;
      }
      case MODE_STATISTICS: {
        if (networkStats == null) {
          tdlib.client().send(new TdApi.GetNetworkStatistics(), this);
        }
        break;
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    org.thunderdog.challegram.data.Tgx101SpeechModels.removeListener(tgx101SpeechListener);
    if (mode == MODE_NONE) {
      TdlibManager.instance().global().removeConnectionListener(this);
      Settings.instance().removeProxyListener(this);
    }
  }

  private TGStorageStatsFast storageStats;

  public interface StorageStatsFastCallback {
    void onStorageStatsFastLoaded (TGStorageStatsFast stats);
  }

  private StorageStatsFastCallback statsCallback;

  public void setStorageStats (final TGStorageStatsFast stats) {
    if (!isDestroyed()) {
      this.storageStats = stats;
      adapter.updateValuedSettingById(R.id.btn_storageUsage);
      if (statsCallback != null) {
        statsCallback.onStorageStatsFastLoaded(stats);
        statsCallback = null;
      }
    }
  }

  public @Nullable TGStorageStatsFast getStorageStats () {
    return storageStats;
  }

  public void setStorageStatsCallback (StorageStatsFastCallback callback) {
    this.statsCallback = callback;
  }

  private TGNetworkStats networkStats;

  private void setNetworkStats (TGNetworkStats stats) {
    this.networkStats = stats;

    if (mode == MODE_STATISTICS) {
      adapter.updateValuedSettingById(R.id.btn_dataUsageWiFi);
      adapter.updateValuedSettingById(R.id.btn_dataUsageMobile);
      adapter.updateValuedSettingById(R.id.btn_dataUsageRoaming);
      adapter.updateValuedSettingById(R.id.btn_resetNetworkStats);

      ViewController<?> c = previousStackItem();
      if (c != null) {
        ((SettingsDataController) c).setNetworkStats(stats);
      }
    } else {
      adapter.updateValuedSettingById(R.id.btn_dataUsageTotal);
    }
  }

  @Override
  public void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.Proxy proxy, String description, boolean isCurrent, boolean isNewAdd) {
    if (isCurrent) {
      adapter.updateValuedSettingById(R.id.btn_proxy);
    }
  }

  @Override
  public void onProxyAvailabilityChanged (boolean isAvailable) { }

  @Override
  public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) { }

  @Override
  public void onResult (final TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.StorageStatisticsFast.CONSTRUCTOR: {
        final TGStorageStatsFast stats = new TGStorageStatsFast((TdApi.StorageStatisticsFast) object, null);
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            setStorageStats(stats);
          }
        });
        break;
      }
      case TdApi.NetworkStatistics.CONSTRUCTOR: {
        final TGNetworkStats stats = new TGNetworkStats((TdApi.NetworkStatistics) object);
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            setNetworkStats(stats);
          }
        });
        break;
      }
      /*case TdApi.ProxyEmpty.CONSTRUCTOR:
      case TdApi.ProxySocks5.CONSTRUCTOR: {
        tdlib.ui().post(new Runnable() {
          @Override
          public void run () {
            if (!isDestroyed()) {
              setProxy((TdApi.Proxy) object);
            }
          }
        });
        break;
      }*/
      case TdApi.Error.CONSTRUCTOR: {
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            if (storageStats == null) {
              setStorageStats(new TGStorageStatsFast(null, null));
            } else {
              UI.showError(object);
            }
          }
        });
        break;
      }
    }
  }

  @Override
  public void onConnectionStateChanged (Tdlib tdlib, int newState, boolean isCurrent) { }

  @Override
  public void onConnectionTypeChanged (int oldConnectionType, int connectionType) {
    if (!isDestroyed()) {
      adapter.updateValuedSettingById(R.id.btn_dataSaver);
      adapter.updateValuedSettingById(R.id.btn_dataSaverForce);
    }
  }

  @Override
  public void onSystemDataSaverStateChanged (boolean isEnabled) {
    if (!isDestroyed()) {
      adapter.updateValuedSettingById(R.id.btn_dataSaver);
      adapter.updateValuedSettingById(R.id.btn_dataSaverForce);
    }
  }

  private String tgx101StoriesFoldersValue () {
    java.util.Set<Integer> chosen = Tgx101Stories.folders();
    if (chosen.isEmpty()) return Lang.getString(R.string.Tgx101StoriesFoldersAll);
    if (chosen.contains(Tgx101Stories.FOLDERS_NONE)) return Lang.getString(R.string.Tgx101StoriesFoldersNone);
    StringBuilder b = new StringBuilder();
    for (TdApi.ChatFolderInfo info : tdlib.chatFolders()) {
      if (chosen.contains(info.id)) {
        if (b.length() > 0) b.append(", ");
        b.append(info.name != null && info.name.text != null ? info.name.text.text : "");
      }
    }
    return b.length() > 0 ? b.toString() : Lang.getString(R.string.Tgx101StoriesFoldersAll);
  }

  private static String tgx101MaskValue (int mask, int[] titles) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < titles.length; i++) {
      if ((mask & (1 << i)) != 0) {
        if (b.length() > 0) b.append(", ");
        b.append(Lang.getString(titles[i]));
      }
    }
    return b.toString();
  }

  /** TGx101: items of a capsule hold menu («Settings» or «Contacts»); at least two stay on */
  private void tgx101ChooseHoldMenu (int settingId, int[] titles, int mask, me.vkryl.core.lambda.RunnableInt save) {
    ListItem[] items = new ListItem[titles.length];
    for (int i = 0; i < items.length; i++) {
      items[i] = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, 2000000 + i, 0, titles[i], (mask & (1 << i)) != 0);
    }
    showSettings(new SettingsWrapBuilder(settingId)
      .addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.Tgx101CapsuleMenuHint))
      .setRawItems(items)
      .setSaveStr(R.string.Done)
      .setIntDelegate((id, result) -> {
        int next = 0;
        for (int i = 0; i < items.length; i++) {
          if (result.get(2000000 + i) != 0) next |= 1 << i;
        }
        if (Integer.bitCount(next) < 2) {
          UI.showToast(R.string.Tgx101CapsuleMenuMin, android.widget.Toast.LENGTH_SHORT);
          return;
        }
        save.runWithInt(next);
        adapter.updateValuedSettingById(settingId);
      }));
  }

  /** Stories from which folders: «All chats», any number of folders, or «None» */
  private void tgx101ChooseStoriesFolders () {
    TdApi.ChatFolderInfo[] folders = tdlib.chatFolders();
    java.util.Set<Integer> chosen = Tgx101Stories.folders();
    java.util.List<ListItem> items = new java.util.ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_tgx101StoriesFoldersAll, 0, R.string.Tgx101StoriesFoldersAll, chosen.isEmpty()));
    for (TdApi.ChatFolderInfo info : folders) {
      String name = info.name != null && info.name.text != null ? info.name.text.text : ("#" + info.id);
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, info.id + 1000000, 0, name, chosen.contains(info.id)));
    }
    items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_tgx101StoriesFoldersNone, 0, R.string.Tgx101StoriesFoldersNone, chosen.contains(Tgx101Stories.FOLDERS_NONE)));
    showSettings(new SettingsWrapBuilder(R.id.btn_tgx101StoriesFolders)
      .addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.Tgx101StoriesFoldersHint))
      .setRawItems(items.toArray(new ListItem[0]))
      .setSaveStr(R.string.Done)
      .setIntDelegate((id, result) -> {
        java.util.Set<Integer> next = new java.util.HashSet<>();
        if (result.get(R.id.btn_tgx101StoriesFoldersNone) != 0) {
          next.add(Tgx101Stories.FOLDERS_NONE);
        } else if (result.get(R.id.btn_tgx101StoriesFoldersAll) == 0) {
          for (TdApi.ChatFolderInfo info : folders) {
            if (result.get(info.id + 1000000) != 0) next.add(info.id);
          }
        }
        Tgx101Stories.setFolders(next);
        adapter.updateValuedSettingById(R.id.btn_tgx101StoriesFolders);
        org.thunderdog.challegram.Tgx101Diag.mark("stories: folders " + next);
      }));
  }

  // TGx101 MagiX sections (user 2026-10-05, approved mockup ~/Desktop/TGX/Макеты/MagiX)

  // TGx101: MagiX → Notifications → «When the app is open», in Settings.TGX101_FOREGROUND_NOTIF_* order
  private static final int[] FOREGROUND_NOTIF_NAMES = {R.string.Tgx101ForegroundNotifShow, R.string.Tgx101ForegroundNotifQuiet, R.string.Tgx101ForegroundNotifHide};

  private static final int[] TGX101_SECTION_IDS = {
    R.id.btn_tgx101SectionChatList, R.id.btn_tgx101SectionStories, R.id.btn_tgx101SectionMessages, R.id.btn_tgx101SectionChannels,
    R.id.btn_tgx101SectionMedia, R.id.btn_tgx101SectionNotifications, R.id.btn_tgx101SectionText, R.id.btn_tgx101SectionCalls,
    R.id.btn_tgx101SectionTranslate, R.id.btn_tgx101SectionData
  };
  private static final int[] TGX101_SECTION_TITLES = {
    R.string.Tgx101SectionChatList, R.string.Tgx101SectionStories, R.string.Tgx101SectionMessages, R.string.Tgx101SectionChannels,
    R.string.Tgx101SectionMedia, R.string.Tgx101SectionNotifications, R.string.Tgx101SectionText, R.string.Tgx101SectionCalls,
    R.string.Tgx101SectionTranslate, R.string.Tgx101SectionData
  };
  private static final int[] TGX101_SECTION_HINTS = {
    R.string.Tgx101SectionChatListHint, R.string.Tgx101SectionStoriesHint, R.string.Tgx101SectionMessagesHint, R.string.Tgx101SectionChannelsHint,
    R.string.Tgx101SectionMediaHint, R.string.Tgx101SectionNotificationsHint, R.string.Tgx101SectionTextHint, R.string.Tgx101SectionCallsHint,
    R.string.Tgx101SectionTranslateHint, R.string.Tgx101SectionDataHint
  };
  private static final int[] TGX101_SECTION_ICONS = {
    R.drawable.baseline_forum_24, R.drawable.baseline_history_24, R.drawable.baseline_chat_bubble_24, R.drawable.baseline_bullhorn_24,
    R.drawable.baseline_camera_alt_24, R.drawable.baseline_notifications_24, R.drawable.baseline_format_text_24, R.drawable.baseline_call_24,
    R.drawable.baseline_translate_24, R.drawable.baseline_data_usage_24
  };

  private static int tgx101SectionIndex (int id) {
    for (int i = 0; i < TGX101_SECTION_IDS.length; i++) {
      if (TGX101_SECTION_IDS[i] == id) return i;
    }
    return -1;
  }

  /** Which section a MagiX setting goes to; the last one (data) also gets anything not listed */
  private static int tgx101SectionOf (ListItem item) {
    int id = item.getId();
    if (id == R.id.btn_toggleNewSetting) {
      long flag = item.getLongId();
      if (flag == Settings.SETTING_FLAG_PAUSE_MEDIA_ON_RECORD || flag == Settings.SETTING_FLAG_SEND_PHOTOS_IN_HD) return 4;
      if (flag == Settings.SETTING_FLAG_KEEP_ALIVE_CONNECTION) return 5; // user 2026-10-06: looked for it under «Notifications»
      return 9;
    }
    if (id == R.id.btn_tgx101Filters || id == R.id.btn_tgx101HideBlocked || id == R.id.btn_pullToSearch || id == R.id.btn_tgx101FloatingInput || id == R.id.btn_tgx101BottomGap) return 0;
    if (id == R.id.btn_tgx101StoriesMode || id == R.id.btn_tgx101StoriesFolders) return 1;
    if (id == R.id.btn_tgx101SwipeActions || id == R.id.btn_tgx101Haptics || id == R.id.btn_tgx101MenuHidesKeyboard || id == R.id.btn_tgx101LongPressMenu || id == R.id.btn_tgx101MenuAtFinger || id == R.id.btn_tgx101MessageMenu || id == R.id.btn_tgx101TextEditor || id == R.id.btn_tgx101MessageMenuHand || id == R.id.btn_tgx101MessageMenuOrder
      || id == R.id.btn_tgx101TapMode || id == R.id.btn_tgx101FormatMenu || id == R.id.btn_tgx101QuickReply1 || id == R.id.btn_tgx101QuickReply2
      || id == R.id.btn_tgx101QuickReply3 || id == R.id.btn_tgx101QuickReply4 || id == R.id.btn_tgx101QuickReply5) return 2;
    if (id == R.id.btn_tgx101HideCompose) return 0;
    if (id == R.id.btn_tgx101HideInputCamera || id == R.id.btn_tgx101HideInputCommands || id == R.id.btn_tgx101HideInputEmoji || id == R.id.btn_tgx101HidePhone || id == R.id.btn_tgx101NavCapsule || id == R.id.btn_tgx101CapsuleScrollHide || id == R.id.btn_tgx101CapsuleMenu || id == R.id.btn_tgx101ContactsMenu || id == R.id.btn_tgx101QuickCalls) return 0;
    if (id == R.id.btn_tgx101RearRounds) return 4;
    if (id == R.id.btn_tgx101HideAllReactions || id == R.id.btn_tgx101HideChannelReactions || id == R.id.btn_tgx101NextChannelSwipe || id == R.id.btn_showDiscussButton || id == R.id.btn_showCommentsButton
      || id == R.id.btn_showChannelMuteButton || id == R.id.btn_hideSubscribeLink || id == R.id.btn_separateChannelPosts) return 3;
    if (id == R.id.btn_tgx101VoiceQueue || id == R.id.btn_tgx101CameraInAttach || id == R.id.btn_tgx101ZoomPullClose || id == R.id.btn_roundVideoQuality
      || id == R.id.btn_roundStabilization) return 4;
    if (id == R.id.btn_tgx101Snooze || id == R.id.btn_tgx101NotificationPlane || id == R.id.btn_tgx101ForegroundNotif) return 5;
    if (id == R.id.btn_tgx101UiText || id == R.id.btn_tgx101UiBold || id == R.id.btn_tgx101ChatListTextSize || id == R.id.btn_tgx101Font || id == R.id.btn_tgx101TextWeight || id == R.id.btn_chatFontSize || id == R.id.btn_bigEmojiSize) return 6;
    if (id == R.id.btn_tgx101CallBar || id == R.id.btn_tgx101NewCallScreen || id == R.id.btn_tgx101CallPhoto || id == R.id.btn_tgx101CallPattern
      || id == R.id.btn_tgx101RingRamp || id == R.id.btn_tgx101RingRampTime) return 7;
    if (id == R.id.btn_tgx101TranslateOnDevice || id == R.id.btn_tgx101TranslateModels || id == R.id.btn_tgx101SpeechModel || id == R.id.btn_tgx101FakeNoPremium) return 8;
    return 9;
  }

  /** The full MagiX list was built; keep the section list (root) or this section's settings, grouped under their old headers */
  private void tgx101ApplySection () {
    List<ListItem> all = new java.util.ArrayList<>(adapter.getItems());
    List<ListItem> out = new java.util.ArrayList<>();
    out.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    if (tgx101Section < 0) {
      out.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      for (int i = 0; i < TGX101_SECTION_IDS.length; i++) {
        if (i > 0) out.add(new ListItem(ListItem.TYPE_SEPARATOR));
        out.add(new ListItem(ListItem.TYPE_VALUED_SETTING, TGX101_SECTION_IDS[i], TGX101_SECTION_ICONS[i], TGX101_SECTION_TITLES[i]));
      }
      out.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.setItems(out.toArray(new ListItem[0]), false);
      return;
    }
    // walk the blocks: HEADER, SHADOW_TOP, items / separators (a description may sit inside), SHADOW_BOTTOM, description
    ListItem header = null;
    List<ListItem> kept = new java.util.ArrayList<>();
    int blockCount = 0;
    boolean afterKeptBlock = false;
    for (int i = 0; i < all.size(); i++) {
      ListItem item = all.get(i);
      int type = item.getViewType();
      if (type == ListItem.TYPE_HEADER) {
        header = item;
        kept.clear();
        blockCount = 0;
        afterKeptBlock = false;
      } else if (type == ListItem.TYPE_SHADOW_TOP) {
        afterKeptBlock = false;
      } else if (type == ListItem.TYPE_SHADOW_BOTTOM) {
        if (!kept.isEmpty()) {
          // the old header only when most of its block came along (a lone «Quiet» is not «Recording and photos»)
          if (header != null && kept.size() * 2 >= blockCount) out.add(header);
          out.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          for (int k = 0; k < kept.size(); k++) {
            if (k > 0) out.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            out.add(kept.get(k));
          }
          out.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          afterKeptBlock = kept.size() == blockCount; // its description only when the whole block is here
        }
        header = null;
        kept.clear();
        blockCount = 0;
      } else if (type == ListItem.TYPE_DESCRIPTION) {
        if (afterKeptBlock && i > 0 && all.get(i - 1).getViewType() == ListItem.TYPE_SHADOW_BOTTOM) out.add(item);
        afterKeptBlock = false;
      } else if (type != ListItem.TYPE_SEPARATOR_FULL && type != ListItem.TYPE_SEPARATOR
        && type != ListItem.TYPE_EMPTY_OFFSET_SMALL && type != ListItem.TYPE_EMPTY_OFFSET && item.getId() != 0) {
        blockCount++;
        if (tgx101SectionOf(item) == tgx101Section) kept.add(item);
      }
    }
    adapter.setItems(out.toArray(new ListItem[0]), false);
  }


  /** TGx101 test builds (user 2026-10-08 11:3x «приведи MagiX в порядок»): settings that are fixed now go, the long chat
   *  list section is split into labelled groups */
  private static ListItem[] tgx101TestItems (ListItem[] raw) {
    java.util.Set<Integer> gone = new java.util.HashSet<>(java.util.Arrays.asList(
      R.id.btn_tgx101FloatingInput, // capsule only (the side menu is back as an option — hotfix 0.1.591)
      R.id.btn_tgx101TextEditor, R.id.btn_tgx101MessageMenuHand, R.id.btn_tgx101MenuAtFinger, // always on / right hand
      R.id.btn_tgx101MenuHidesKeyboard)); // the keyboard never closes for the menu
    if (org.thunderdog.challegram.BuildConfig.TGX101_TEST) {
      gone.add(R.id.btn_tgx101NewCallScreen); // always on in test builds (user 2026-10-08)
      gone.add(R.id.btn_tgx101ZoomPullClose);
    }
    int[] bottomMenu = {R.id.btn_tgx101CapsuleMenu, R.id.btn_tgx101ContactsMenu, R.id.btn_tgx101QuickCalls, R.id.btn_tgx101BottomGap, R.id.btn_tgx101HideCompose};
    int[] inputButtons = {R.id.btn_tgx101HideInputCamera, R.id.btn_tgx101HideInputCommands, R.id.btn_tgx101HideInputEmoji};
    int[] other = {R.id.btn_tgx101RearRounds, R.id.btn_tgx101HidePhone, R.id.btn_tgx101NotificationPlane, R.id.btn_tgx101ForegroundNotif};
    java.util.Map<Integer, ListItem> byId = new java.util.HashMap<>();
    byId.put(R.id.btn_tgx101HideCompose, new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_tgx101HideCompose, 0, R.string.Tgx101HideCompose));
    for (ListItem item : raw) if (item.getId() != 0) byId.put(item.getId(), item);
    java.util.Set<Integer> moved = new java.util.HashSet<>();
    for (int[] g : new int[][] {bottomMenu, inputButtons, other}) for (int id : g) moved.add(id);
    java.util.List<ListItem> out = new java.util.ArrayList<>();
    for (int i = 0; i < raw.length; i++) {
      ListItem item = raw[i];
      int id = item.getId();
      if (gone.contains(id) || moved.contains(id)) {
        if (i + 1 < raw.length && raw[i + 1].getViewType() == ListItem.TYPE_SEPARATOR_FULL) i++;
        continue;
      }
      // the bottom gap moved out: the chat list section's hint keeps only its pull-to-search part
      if (item.getViewType() == ListItem.TYPE_SEPARATOR_FULL && i + 1 < raw.length && raw[i + 1].getViewType() == ListItem.TYPE_DESCRIPTION && !out.isEmpty() && out.get(out.size() - 1).getViewType() == ListItem.TYPE_SEPARATOR_FULL) {
        continue;
      }
      out.add(item);
      if (item.getViewType() == ListItem.TYPE_SHADOW_BOTTOM && i > 0 && tgx101SectionHas(raw, i, R.id.btn_pullToSearch)) {
        tgx101AddGroup(out, byId, R.string.Tgx101SectionBottomMenu, bottomMenu, 0);
        tgx101AddGroup(out, byId, R.string.Tgx101SectionInputButtons, inputButtons, R.string.Tgx101SectionInputButtonsHint);
        tgx101AddGroup(out, byId, R.string.Tgx101SectionOther, other, 0);
      }
    }
    // no separator right before a section's end
    for (int i = out.size() - 2; i >= 0; i--) {
      if (out.get(i).getViewType() == ListItem.TYPE_SEPARATOR_FULL && (out.get(i + 1).getViewType() == ListItem.TYPE_SHADOW_BOTTOM || out.get(i + 1).getViewType() == ListItem.TYPE_DESCRIPTION)) out.remove(i);
    }
    return out.toArray(new ListItem[0]);
  }

  private static boolean tgx101SectionHas (ListItem[] raw, int shadowBottom, int id) {
    for (int i = shadowBottom - 1; i >= 0 && raw[i].getViewType() != ListItem.TYPE_SHADOW_TOP; i--) if (raw[i].getId() == id) return true;
    return false;
  }

  private static void tgx101AddGroup (java.util.List<ListItem> out, java.util.Map<Integer, ListItem> byId, int title, int[] ids, int hint) {
    out.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, title));
    out.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    boolean first = true;
    for (int id : ids) {
      ListItem item = byId.get(id);
      if (item == null) continue;
      if (!first) out.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      out.add(item);
      first = false;
    }
    out.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    if (hint != 0) out.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, hint));
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    final boolean toggleResult = adapter.toggleView(v);
    int tgx101SectionIndex = tgx101SectionIndex(id);
    if (tgx101SectionIndex != -1) {
      if (tgx101SectionIndex == TGX101_SECTION_IDS.length - 1) {
        navigateTo(new SettingsDataController(context, tdlib)); // Telegram X's own «Data and storage»
        return;
      }
      SettingsDataController c = new SettingsDataController(context, tdlib);
      c.setArguments(new Args(MODE_TGX101).setTgx101Section(tgx101SectionIndex));
      navigateTo(c);
      return;
    }


    if (id == R.id.btn_resetNetworkStats) {
      showOptions(Lang.getString(R.string.ResetStatsHint), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Reset), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
        if (optionId == R.id.btn_delete) {
          tdlib.client().send(new TdApi.ResetNetworkStatistics(), object -> {
            switch (object.getConstructor()) {
              case TdApi.Ok.CONSTRUCTOR: {
                tdlib.client().send(new TdApi.GetNetworkStatistics(), SettingsDataController.this);
                break;
              }
              case TdApi.Error.CONSTRUCTOR: {
                UI.showError(object);
                break;
              }
            }
          });
        }
        return true;
      });
    } else if (id == R.id.btn_storageUsage) {
      SettingsCacheController cacheController = new SettingsCacheController(context, tdlib);
      cacheController.setArguments(this);
      navigateTo(cacheController);
    } else if (id == R.id.btn_dataUsageTotal) {
      if (networkStats == null) {
        return;
      }
      SettingsDataController c = new SettingsDataController(context, tdlib);
      c.setArguments(new Args(MODE_STATISTICS).setData(networkStats));
      navigateTo(c);
    } else if (id == R.id.btn_dataUsageMobile || id == R.id.btn_dataUsageWiFi || id == R.id.btn_dataUsageRoaming) {
      if (networkStats == null) {
        return;
      }

      int type;
      if (id == R.id.btn_dataUsageMobile) {
        type = TGNetworkStats.TYPE_MOBILE;
      } else if (id == R.id.btn_dataUsageRoaming) {
        type = TGNetworkStats.TYPE_ROAMING;
      } else if (id == R.id.btn_dataUsageWiFi) {
        type = TGNetworkStats.TYPE_WIFI;
      } else {
        return;
      }

      SettingsNetworkStatsController c = new SettingsNetworkStatsController(context, tdlib);
      c.setArguments(new SettingsNetworkStatsController.Args(type, networkStats));
      navigateTo(c);
    } else if (id == R.id.btn_tgx101RingRampTime) {
      int current = Settings.instance().getTgx101RingRampSeconds();
      int[] ids = {R.id.btn_tgx101RingRamp5, R.id.btn_tgx101RingRamp10, R.id.btn_tgx101RingRamp15, R.id.btn_tgx101RingRamp20, R.id.btn_tgx101RingRamp30};
      ListItem[] items = new ListItem[ids.length];
      for (int k = 0; k < ids.length; k++) {
        int seconds = Settings.TGX101_RING_RAMP_OPTIONS[k];
        items[k] = new ListItem(ListItem.TYPE_RADIO_OPTION, ids[k], 0, Lang.getString(R.string.Tgx101RingRampSeconds, seconds), id, current == seconds);
      }
      showSettings(new SettingsWrapBuilder(id).addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.Tgx101RingRampHint)).setRawItems(items).setIntDelegate(this));
    } else if (id == R.id.btn_lessDataForCalls) {
      showSettings(new SettingsWrapBuilder(id).addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.UseLessDataForCallsDesc)).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_disabled, 0, R.string.Never, id, tdlib.files().getVoipDataSavingOption() == DataSavingOption.NEVER),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roaming, 0, R.string.OnRoaming, id, tdlib.files().getVoipDataSavingOption() == DataSavingOption.ROAMING),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_mobile, 0, R.string.OnMobileNetwork, id, tdlib.files().getVoipDataSavingOption() == DataSavingOption.MOBILE),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_always, 0, R.string.UseLessDataAlways, id, tdlib.files().getVoipDataSavingOption() == DataSavingOption.ALWAYS)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_tgx101FloatingInput) {
      Settings.instance().setUseFloatingInput(toggleResult); // the view was already toggled above; applies to chats opened afterwards
    } else if (id == R.id.btn_tgx101BottomGap) {
      Settings.instance().setBottomGapEnabled(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101NotificationPlane) {
      Settings.instance().setTgx101NotificationPlane(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101Filters) {
      navigateTo(new Tgx101FiltersController(context, tdlib));
    } else if (id == R.id.btn_pullToSearch) {
      Settings.instance().setPullToSearchEnabled(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_showDiscussButton) {
      Settings.instance().setShowDiscussButton(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101CallPhoto) {
      showCallPhotoModes();
    } else if (id == R.id.btn_tgx101CallPattern) {
      Settings.instance().setCallPattern(toggleResult ? Settings.CALL_PATTERN_PAPER_PLANES : Settings.CALL_PATTERN_NONE); // the view was already toggled above
    } else if (id == R.id.btn_tgx101RingRamp) {
      Settings.instance().setRingRampEnabled(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101CapsuleMenu) {
      tgx101ChooseHoldMenu(id, MainController.TGX101_CAPSULE_MENU_TITLES, Settings.instance().tgx101CapsuleMenu(), mask -> Settings.instance().setTgx101CapsuleMenu(mask));
    } else if (id == R.id.btn_tgx101ContactsMenu) {
      tgx101ChooseHoldMenu(id, MainController.TGX101_CONTACTS_MENU_TITLES, Settings.instance().tgx101ContactsMenu(), mask -> Settings.instance().setTgx101ContactsMenu(mask));
    } else if (id == R.id.btn_tgx101QuickCalls) {
      Tgx101QuickCalls.openEditor(this, tdlib);
    } else if (id == R.id.btn_tgx101StoriesFolders) {
      tgx101ChooseStoriesFolders();
    } else if (id == R.id.btn_tgx101StoriesMode) {
      int current = Tgx101Stories.mode();
      showOptions(Lang.getString(R.string.Tgx101StoriesMode), new int[] {1, 2},
        new String[] {
          Lang.getString(R.string.Tgx101StoriesModeStrip) + (current == Tgx101Stories.MODE_STRIP ? "  ✓" : ""),
          Lang.getString(R.string.Tgx101StoriesModeRings) + (current == Tgx101Stories.MODE_RINGS ? "  ✓" : "")
        }, null, null, (itemView, optionId) -> {
          Tgx101Stories.setMode(optionId == 2 ? Tgx101Stories.MODE_RINGS : Tgx101Stories.MODE_STRIP);
          adapter.updateValuedSettingById(R.id.btn_tgx101StoriesMode);
          UI.showToast(R.string.Tgx101StoriesModeRestart, android.widget.Toast.LENGTH_SHORT);
          return true;
        });
    } else if (id == R.id.btn_tgx101NextChannelSwipe) {
      Settings.instance().setTgx101NextChannelSwipe(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101HideAllReactions) {
      Settings.instance().setTgx101HideAllReactions(toggleResult);
    } else if (id == R.id.btn_tgx101HideInputCamera) {
      Settings.instance().setTgx101HideInputCamera(toggleResult);
    } else if (id == R.id.btn_tgx101HideInputCommands) {
      Settings.instance().setTgx101HideInputCommands(toggleResult);
    } else if (id == R.id.btn_tgx101HideCompose) {
      Settings.instance().setTgx101HideCompose(toggleResult); // user 2026-10-08 «дай возможность убирать синий карандаш»
      UI.showToast(R.string.Tgx101AppliesAfterRestart, android.widget.Toast.LENGTH_SHORT);
    } else if (id == R.id.btn_tgx101CapsuleScrollHide) {
      Settings.instance().setTgx101CapsuleScrollHide(toggleResult); // user 2026-10-07 22:46 «дай возможность его не скрывать»
    } else if (id == R.id.btn_tgx101NavCapsule) {
      Settings.instance().setTgx101NavCapsule(toggleResult);
      UI.showToast(R.string.Tgx101AppliesAfterRestart, android.widget.Toast.LENGTH_SHORT);
    } else if (id == R.id.btn_tgx101HideInputEmoji) {
      Settings.instance().setTgx101HideInputEmoji(toggleResult);
    } else if (id == R.id.btn_tgx101RearRounds) {
      Settings.instance().setStartRoundWithRear(toggleResult);
    } else if (id == R.id.btn_tgx101HidePhone) {
      Settings.instance().setHidePhoneNumber(toggleResult);
    } else if (id == R.id.btn_tgx101HideChannelReactions) {
      Settings.instance().setTgx101HideChannelReactions(toggleResult); // the view was already toggled above; chats opened afterwards
    } else if (id == R.id.btn_tgx101ZoomPullClose) {
      Settings.instance().setTgx101ZoomPullClose(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101CameraInAttach) {
      Settings.instance().setCameraInAttach(toggleResult); // the view was already toggled above; applies to chats opened afterwards
    } else if (id == R.id.btn_tgx101VoiceQueue) {
      boolean skip = Settings.instance().skipOwnVoiceInQueue();
      showSettings(new SettingsWrapBuilder(R.id.btn_tgx101VoiceQueue)
        .setRawItems(new ListItem[] {
          new ListItem(ListItem.TYPE_RADIO_OPTION, 1, 0, R.string.Tgx101VoiceQueueAll, R.id.btn_tgx101VoiceQueue, !skip),
          new ListItem(ListItem.TYPE_RADIO_OPTION, 2, 0, R.string.Tgx101VoiceQueueSkipOwn, R.id.btn_tgx101VoiceQueue, skip)
        })
        .setIntDelegate((resultId, result) -> {
          Settings.instance().setSkipOwnVoiceInQueue(result.get(R.id.btn_tgx101VoiceQueue) == 2);
          adapter.updateValuedSettingById(R.id.btn_tgx101VoiceQueue);
        }));
    } else if (id == R.id.btn_tgx101SwipeActions) {
      Settings.instance().setTgx101SwipeActions(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101LongPressMenu) {
      Settings.instance().setTgx101LongPressMenu(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101Haptics) {
      Settings.instance().setTgx101Haptics(toggleResult);
    } else if (id == R.id.btn_tgx101HideBlocked) {
      Settings.instance().setTgx101HideBlocked(toggleResult);
      org.thunderdog.challegram.data.Tgx101BlockedSenders.refresh(tdlib, true);
    } else if (id == R.id.btn_tgx101MenuHidesKeyboard) {
      Settings.instance().setTgx101MenuHidesKeyboard(toggleResult);
    } else if (id == R.id.btn_tgx101MenuAtFinger) {
      Settings.instance().setTgx101MenuAtFinger(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101UiBold) {
      Settings.instance().setTgx101UiBold(toggleResult); // the view was already toggled above; menus opened afterwards
    } else if (id == R.id.btn_tgx101UiText) {
      int current = Settings.instance().tgx101UiTextSize();
      int[] values = {100, 115, 130, 145, 160};
      ListItem[] items = new ListItem[values.length];
      for (int i = 0; i < values.length; i++) {
        items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, i + 1, 0, values[i] + "%", R.id.btn_tgx101UiText, values[i] == current);
      }
      showSettings(new SettingsWrapBuilder(R.id.btn_tgx101UiText)
        .setRawItems(items)
        .setIntDelegate((resultId, result) -> {
          int index = result.get(R.id.btn_tgx101UiText) - 1;
          if (index >= 0 && index < values.length) Settings.instance().setTgx101UiTextSize(values[index]);
          adapter.updateValuedSettingById(R.id.btn_tgx101UiText);
        }));
    } else if (id == R.id.btn_tgx101ForegroundNotif) {
      int current = Settings.instance().tgx101ForegroundNotifMode();
      ListItem[] items = new ListItem[FOREGROUND_NOTIF_NAMES.length];
      for (int i = 0; i < items.length; i++) {
        items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, i + 1, 0, FOREGROUND_NOTIF_NAMES[i], R.id.btn_tgx101ForegroundNotif, i == current);
      }
      showSettings(new SettingsWrapBuilder(R.id.btn_tgx101ForegroundNotif)
        .setRawItems(items)
        .setIntDelegate((resultId, result) -> {
          int index = result.get(R.id.btn_tgx101ForegroundNotif) - 1;
          if (index >= 0 && index < FOREGROUND_NOTIF_NAMES.length) Settings.instance().setTgx101ForegroundNotifMode(index);
          adapter.updateValuedSettingById(R.id.btn_tgx101ForegroundNotif);
        }));
    } else if (id == R.id.btn_tgx101Snooze) {
      int current = Settings.instance().tgx101SnoozeMinutes();
      int[] values = {5, 10, 30, 60, 180, 0};
      ListItem[] items = new ListItem[values.length];
      for (int i = 0; i < values.length; i++) {
        items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, i + 1, 0, values[i] > 0 ? Lang.getString(R.string.Tgx101SnoozeMinutes, values[i]) : Lang.getString(R.string.Tgx101SnoozeOff), R.id.btn_tgx101Snooze, values[i] == current);
      }
      showSettings(new SettingsWrapBuilder(R.id.btn_tgx101Snooze)
        .setRawItems(items)
        .setIntDelegate((resultId, result) -> {
          int index = result.get(R.id.btn_tgx101Snooze) - 1;
          if (index >= 0 && index < values.length) Settings.instance().setTgx101SnoozeMinutes(values[index]);
          adapter.updateValuedSettingById(R.id.btn_tgx101Snooze);
        }));
    } else if (id == R.id.btn_tgx101TapMode) {
      showTapModes();
    } else if (id == R.id.btn_tgx101NewCallScreen) {
      Settings.instance().setUseNewCallScreen(toggleResult); // the view was already toggled above
    } else if (quickReplyIndex(id) != -1) {
      final int index = quickReplyIndex(id);
      openInputAlert(Lang.getString(R.string.Tgx101QuickReplyN, index + 1), Lang.getString(R.string.Tgx101QuickReplies), R.string.Done, R.string.Cancel, Settings.instance().getQuickReply(index), (inputView, result) -> {
        Settings.instance().setQuickReply(index, result);
        adapter.updateValuedSettingById(id);
        return true;
      }, true);
    } else if (id == R.id.btn_tgx101TranslateOnDevice) {
      Settings.instance().setTgx101TranslateOnDevice(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101SpeechModel) {
      showTgx101SpeechModels();
    } else if (id == R.id.btn_tgx101TranslateModels) {
      showTgx101TranslateModels();
    } else if (id == R.id.btn_tgx101FakeNoPremium) {
      Settings.instance().setTgx101FakeNoPremium(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_showChannelMuteButton) {
      Settings.instance().setShowChannelMuteButton(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_showCommentsButton) {
      Settings.instance().setShowCommentsButton(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_hideSubscribeLink) {
      Settings.instance().setHideChannelSubscribeLink(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_tgx101CheckUpdates) {
      org.thunderdog.challegram.service.Tgx101Updates.setEnabled(context(), toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_separateChannelPosts) {
      Settings.instance().setSeparateChannelPosts(toggleResult); // the view was already toggled above
    } else if (id == R.id.btn_roundStabilization) {
      int mode = Settings.instance().getRoundStabilizationMode();
      showSettings(new SettingsWrapBuilder(id).addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.RoundStabilizationHint)).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roundStabilizationOff, 0, R.string.RoundStabilizationOff, id, mode == Settings.ROUND_STABILIZATION_OFF),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roundStabilizationSystem, 0, R.string.RoundStabilizationSystem, id, mode == Settings.ROUND_STABILIZATION_SYSTEM),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roundStabilizationGyro, 0, R.string.RoundStabilizationGyro, id, mode == Settings.ROUND_STABILIZATION_GYRO),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roundStabilizationCamera2, 0, R.string.RoundStabilizationCamera2, id, mode == Settings.ROUND_STABILIZATION_CAMERA2)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_roundVideoQuality) {
      int quality = Settings.instance().getRoundVideoQuality();
      showSettings(new SettingsWrapBuilder(id).addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.RoundVideoQualityHint)).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roundQualitySd, 0, R.string.RoundVideoQualitySd, id, quality == Settings.ROUND_VIDEO_QUALITY_SD),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roundQualityHq, 0, R.string.RoundVideoQualityHq, id, quality == Settings.ROUND_VIDEO_QUALITY_HQ),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roundQualityHigh, 0, R.string.RoundVideoQualityHigh, id, quality == Settings.ROUND_VIDEO_QUALITY_HIGH),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roundQualityMax, 0, R.string.RoundVideoQualityMax, id, quality == Settings.ROUND_VIDEO_QUALITY_MAX)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_tgx101MessageMenu) {
      Settings.instance().setUseTgx101MessageMenu(toggleResult);
    } else if (id == R.id.btn_tgx101TextEditor) {
      Settings.instance().setUseTgx101TextEditor(toggleResult);
    } else if (id == R.id.btn_tgx101CallBar) {
      Settings.instance().setShowTgx101CallBar(toggleResult);
    } else if (id == R.id.btn_tgx101MessageMenuHand) {
      boolean left = Settings.instance().isTgx101MessageMenuLeftHand();
      showSettings(new SettingsWrapBuilder(id).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101MessageMenuHandRight, 0, R.string.Tgx101MessageMenuHandRight, id, !left),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101MessageMenuHandLeft, 0, R.string.Tgx101MessageMenuHandLeft, id, left)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_tgx101FormatMenu) {
      navigateTo(new Tgx101FormatMenuController(context, tdlib));
    } else if (id == R.id.btn_tgx101MessageMenuOrder) {
      navigateTo(new Tgx101MenuOrderController(context, tdlib));
    } else if (id == R.id.btn_tgx101ChatListTextSize) {
      int size = Settings.instance().getChatListTextSize();
      showSettings(new SettingsWrapBuilder(id).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101ChatListTextSize100, 0, R.string.Tgx101ChatListTextSize100, id, size == 100),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101ChatListTextSize110, 0, R.string.Tgx101ChatListTextSize110, id, size == 110),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101ChatListTextSize120, 0, R.string.Tgx101ChatListTextSize120, id, size == 120),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101ChatListTextSize130, 0, R.string.Tgx101ChatListTextSize130, id, size == 130)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_tgx101TextWeight) {
      int weight = Settings.instance().getTextWeight();
      showSettings(new SettingsWrapBuilder(id).addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.Tgx101TextWeightHint)).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101TextWeight0, 0, R.string.Tgx101TextWeightNormal, id, weight == Settings.TEXT_WEIGHT_NORMAL),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101TextWeight1, 0, R.string.Tgx101TextWeightSlightlyBolder, id, weight == Settings.TEXT_WEIGHT_SLIGHTLY_BOLDER),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101TextWeight2, 0, R.string.Tgx101TextWeightSemibold, id, weight == Settings.TEXT_WEIGHT_SEMIBOLD)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_tgx101Font) {
      int appFont = Settings.instance().getAppFont();
      showSettings(new SettingsWrapBuilder(id).addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.Tgx101FontHint)).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101FontManrope, 0, R.string.Tgx101FontManrope, id, appFont == Settings.APP_FONT_MANROPE),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101FontRoboto, 0, R.string.Tgx101FontRoboto, id, appFont == Settings.APP_FONT_ROBOTO),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_tgx101FontSystem, 0, R.string.Tgx101FontSystem, id, appFont == Settings.APP_FONT_SYSTEM)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_chatFontSize) {
      MessagesController controller = new MessagesController(context, tdlib);
      controller.setArguments(new MessagesController.Arguments(MessagesController.PREVIEW_MODE_FONT_SIZE, null, null));
      navigateTo(controller);
    } else if (id == R.id.btn_bigEmojiSize) {
      int size = Settings.instance().getBigEmojiSize();
      showSettings(new SettingsWrapBuilder(id).addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.BigEmojiSizeHint)).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_bigEmojiSizeOff, 0, R.string.BigEmojiSizeOff, id, size == Settings.BIG_EMOJI_SIZE_OFF),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_bigEmojiSize60, 0, R.string.BigEmojiSize60, id, size == 60),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_bigEmojiSize80, 0, R.string.BigEmojiSize80, id, size == 80),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_bigEmojiSize100, 0, R.string.BigEmojiSize100, id, size == 100),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_bigEmojiSize125, 0, R.string.BigEmojiSize125, id, size == 125)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_proxy) {
      tdlib.ui().openProxySettings(this, true);
    } else if (id == R.id.btn_dataSaver) {
      if (tdlib.files().setDataSaverEnabled(toggleResult)) {
        adapter.updateValuedSettingById(R.id.btn_dataSaverForce);
      }
    } else if (id == R.id.btn_dataSaverForce) {
      showSettings(id, new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_forceMobile, 0, R.string.WhenUsingMobileData, tdlib.files().isDataSaverEnabledOverMobile()),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_forceRoaming, 0, R.string.WhenUsingRoaming, tdlib.files().isDataSaverEnabledOverRoaming())
      }, this);
    } else if (id == R.id.btn_inPrivateChats || id == R.id.btn_inGroupChats || id == R.id.btn_inChannelChats || id == R.id.btn_mediaMobileLimits || id == R.id.btn_mediaWiFiLimits || id == R.id.btn_mediaRoamingLimits) {
      int flags;
      int sizeOption = 0;
      int size = 0;
      if (id == R.id.btn_inPrivateChats) {
        flags = tdlib.files().getDownloadInPrivateChats();
      } else if (id == R.id.btn_inGroupChats) {
        flags = tdlib.files().getDownloadInGroupChats();
      } else if (id == R.id.btn_inChannelChats) {
        flags = tdlib.files().getDownloadInChannelChats();
      } else if (id == R.id.btn_mediaMobileLimits) {
        flags = tdlib.files().getExcludeOverMobile();
        size = tdlib.files().getDownloadLimitOverMobile();
        sizeOption = R.id.btn_size;
      } else if (id == R.id.btn_mediaWiFiLimits) {
        flags = tdlib.files().getExcludeOverWiFi();
        size = tdlib.files().getDownloadLimitOverWifi();
        sizeOption = R.id.btn_size;
      } else if (id == R.id.btn_mediaRoamingLimits) {
        flags = tdlib.files().getExcludeOverRoaming();
        size = tdlib.files().getDownloadLimitOverRoaming();
        sizeOption = R.id.btn_size;
      } else {
        throw new RuntimeException();
      }

      int currentValue = 0;
      String[] sizeOptions;
      if (sizeOption != 0) {
        sizeOptions = new String[TdlibFilesManager.DOWNLOAD_LIMIT_OPTIONS.length];
        for (int i = 0; i < sizeOptions.length; i++) {
          sizeOptions[i] = TdlibFilesManager.getDownloadLimitString(TdlibFilesManager.DOWNLOAD_LIMIT_OPTIONS[i]);
          if (TdlibFilesManager.DOWNLOAD_LIMIT_OPTIONS[i] == size) {
            currentValue = i;
          }
        }
      } else {
        sizeOptions = null;
      }

      showSettings(new SettingsWrapBuilder(id).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_photos, 0, sizeOption != 0 ? R.string.NoPhotos : R.string.Photos, (flags & TdlibFilesManager.DOWNLOAD_FLAG_PHOTO) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_voice, 0, sizeOption != 0 ? R.string.NoVoiceMessages : R.string.VoiceMessages, (flags & TdlibFilesManager.DOWNLOAD_FLAG_VOICE) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_videoNote, 0, sizeOption != 0 ? R.string.NoVideoMessages : R.string.VideoMessages, (flags & TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_video, 0, sizeOption != 0 ? R.string.NoVideos : R.string.Videos, (flags & TdlibFilesManager.DOWNLOAD_FLAG_VIDEO) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_file, 0, sizeOption != 0 ? R.string.NoFiles : R.string.Files, (flags & TdlibFilesManager.DOWNLOAD_FLAG_FILE) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_music, 0, sizeOption != 0 ? R.string.NoMusic : R.string.Music, (flags & TdlibFilesManager.DOWNLOAD_FLAG_MUSIC) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_gif, 0, sizeOption != 0 ? R.string.NoGIFs : R.string.GIFs, (flags & TdlibFilesManager.DOWNLOAD_FLAG_GIF) != 0),
      }).setIntDelegate(this).setSizeOptionId(sizeOption).setSizeValue(currentValue).setSizeValues(sizeOptions).setAllowResize(false));
    } else if (id == R.id.btn_cacheSettings) {
      navigateTo(new SettingsCacheController(context, tdlib));
    } else if (id == R.id.btn_toggleNewSetting) {
      applySettingToggle((ListItem) v.getTag(), toggleResult);
    } else if (id == R.id.btn_showAdvanced) {
      final int index = adapter.indexOfViewById(R.id.btn_showAdvanced);

      if (index != -1) {
        List<ListItem> items = adapter.getItems();

        items.remove(index);
        items.remove(index);

        ListItem[] advancedItems = newAdvancedItems();

        ArrayUtils.ensureCapacity(items, items.size() + advancedItems.length);
        int i = index;
        for (ListItem item : advancedItems) {
          items.add(i++, item);
        }

        adapter.notifyItemRangeRemoved(index, 2);
        adapter.notifyItemRangeInserted(index, advancedItems.length);
      }
    }
  }

  private ListItem[] newAdvancedItems () {
    return new ListItem[] {
      new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_mediaMobileLimits, 0, R.string.RestrictOverMobile),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_mediaWiFiLimits, 0, R.string.RestrictOnWiFi),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_mediaRoamingLimits, 0, R.string.RestrictOnRoaming),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
      // new SettingItem(SettingItem.TYPE_DESCRIPTION, 0, 0, R.string.SizeLimitDesc)
    };
  }

  private static int roundStabilizationName (int mode) {
    switch (mode) {
      case Settings.ROUND_STABILIZATION_OFF: return R.string.RoundStabilizationOff;
      case Settings.ROUND_STABILIZATION_SYSTEM: return R.string.RoundStabilizationSystem;
      case Settings.ROUND_STABILIZATION_CAMERA2: return R.string.RoundStabilizationCamera2;
      default: return R.string.RoundStabilizationGyro;
    }
  }

  private static int chatListTextSizeName (int size) {
    switch (size) {
      case 110: return R.string.Tgx101ChatListTextSize110;
      case 120: return R.string.Tgx101ChatListTextSize120;
      case 130: return R.string.Tgx101ChatListTextSize130;
      default: return R.string.Tgx101ChatListTextSize100;
    }
  }

  private static int textWeightName (int weight) {
    switch (weight) {
      case Settings.TEXT_WEIGHT_SLIGHTLY_BOLDER: return R.string.Tgx101TextWeightSlightlyBolder;
      case Settings.TEXT_WEIGHT_SEMIBOLD: return R.string.Tgx101TextWeightSemibold;
      default: return R.string.Tgx101TextWeightNormal;
    }
  }

  private static int bigEmojiSizeName (int size) {
    switch (size) {
      case Settings.BIG_EMOJI_SIZE_OFF: return R.string.BigEmojiSizeOff;
      case 60: return R.string.BigEmojiSize60;
      case 80: return R.string.BigEmojiSize80;
      case 125: return R.string.BigEmojiSize125;
      default: return R.string.BigEmojiSize100;
    }
  }

  private static int roundVideoQualityName (int quality) {
    switch (quality) {
      case Settings.ROUND_VIDEO_QUALITY_SD: return R.string.RoundVideoQualitySd;
      case Settings.ROUND_VIDEO_QUALITY_HQ: return R.string.RoundVideoQualityHq;
      case Settings.ROUND_VIDEO_QUALITY_MAX: return R.string.RoundVideoQualityMax;
      default: return R.string.RoundVideoQualityHigh;
    }
  }

  @Override
  public void onApplySettings (@IdRes int id, SparseIntArray result) {
    if (id == R.id.btn_dataSaverForce) {
      final boolean forceMobile = result.get(R.id.btn_forceMobile) != 0;
      final boolean forceRoaming = result.get(R.id.btn_forceRoaming) != 0;

      if (tdlib.files().setDataSaverForcedOptions(forceMobile, forceRoaming)) {
        adapter.updateValuedSettingById(R.id.btn_dataSaver);
        adapter.updateValuedSettingById(id);
      }
    } else if (id == R.id.btn_roundStabilization) {
      final int res = result.get(R.id.btn_roundStabilization);
      int mode;
      if (res == R.id.btn_roundStabilizationOff) {
        mode = Settings.ROUND_STABILIZATION_OFF;
      } else if (res == R.id.btn_roundStabilizationSystem) {
        mode = Settings.ROUND_STABILIZATION_SYSTEM;
      } else if (res == R.id.btn_roundStabilizationCamera2) {
        mode = Settings.ROUND_STABILIZATION_CAMERA2;
      } else {
        mode = Settings.ROUND_STABILIZATION_GYRO;
      }
      Settings.instance().setRoundStabilizationMode(mode);
      adapter.updateValuedSettingById(R.id.btn_roundStabilization);
    } else if (id == R.id.btn_tgx101MessageMenuHand) {
      Settings.instance().setTgx101MessageMenuLeftHand(result.get(R.id.btn_tgx101MessageMenuHand) == R.id.btn_tgx101MessageMenuHandLeft);
      adapter.updateValuedSettingById(R.id.btn_tgx101MessageMenuHand);
    } else if (id == R.id.btn_tgx101ChatListTextSize) {
      final int res = result.get(R.id.btn_tgx101ChatListTextSize);
      int size = res == R.id.btn_tgx101ChatListTextSize110 ? 110 : res == R.id.btn_tgx101ChatListTextSize120 ? 120 : res == R.id.btn_tgx101ChatListTextSize130 ? 130 : 100;
      Settings.instance().setChatListTextSize(size);
      adapter.updateValuedSettingById(R.id.btn_tgx101ChatListTextSize);
    } else if (id == R.id.btn_tgx101Font) {
      int res = result.get(R.id.btn_tgx101Font);
      int appFont = res == R.id.btn_tgx101FontSystem ? Settings.APP_FONT_SYSTEM : res == R.id.btn_tgx101FontRoboto ? Settings.APP_FONT_ROBOTO : Settings.APP_FONT_MANROPE;
      if (appFont != Settings.instance().getAppFont()) {
        Settings.instance().setAppFont(appFont);
        adapter.updateValuedSettingById(R.id.btn_tgx101Font);
        UI.showToast(R.string.Tgx101FontRestart, android.widget.Toast.LENGTH_LONG);
      }
    } else if (id == R.id.btn_tgx101TextWeight) {
      final int res = result.get(R.id.btn_tgx101TextWeight);
      int weight = res == R.id.btn_tgx101TextWeight1 ? Settings.TEXT_WEIGHT_SLIGHTLY_BOLDER : res == R.id.btn_tgx101TextWeight2 ? Settings.TEXT_WEIGHT_SEMIBOLD : Settings.TEXT_WEIGHT_NORMAL;
      if (weight != Settings.instance().getTextWeight()) {
        Settings.instance().setTextWeight(weight);
        adapter.updateValuedSettingById(R.id.btn_tgx101TextWeight);
        UI.showToast(R.string.Tgx101TextWeightRestart, android.widget.Toast.LENGTH_LONG);
      }
    } else if (id == R.id.btn_bigEmojiSize) {
      final int res = result.get(R.id.btn_bigEmojiSize);
      int size;
      if (res == R.id.btn_bigEmojiSizeOff) {
        size = Settings.BIG_EMOJI_SIZE_OFF;
      } else if (res == R.id.btn_bigEmojiSize60) {
        size = 60;
      } else if (res == R.id.btn_bigEmojiSize80) {
        size = 80;
      } else if (res == R.id.btn_bigEmojiSize125) {
        size = 125;
      } else {
        size = Settings.BIG_EMOJI_SIZE_DEFAULT;
      }
      Settings.instance().setBigEmojiSize(size);
      adapter.updateValuedSettingById(R.id.btn_bigEmojiSize);
    } else if (id == R.id.btn_roundVideoQuality) {
      final int res = result.get(R.id.btn_roundVideoQuality);
      int quality;
      if (res == R.id.btn_roundQualitySd) {
        quality = Settings.ROUND_VIDEO_QUALITY_SD;
      } else if (res == R.id.btn_roundQualityHq) {
        quality = Settings.ROUND_VIDEO_QUALITY_HQ;
      } else if (res == R.id.btn_roundQualityMax) {
        quality = Settings.ROUND_VIDEO_QUALITY_MAX;
      } else {
        quality = Settings.ROUND_VIDEO_QUALITY_HIGH;
      }
      Settings.instance().setRoundVideoQuality(quality);
      adapter.updateValuedSettingById(R.id.btn_roundVideoQuality);
    } else if (id == R.id.btn_tgx101RingRampTime) {
      final int res = result.get(R.id.btn_tgx101RingRampTime);
      int seconds = res == R.id.btn_tgx101RingRamp5 ? 5 : res == R.id.btn_tgx101RingRamp10 ? 10 : res == R.id.btn_tgx101RingRamp15 ? 15 : res == R.id.btn_tgx101RingRamp20 ? 20 : 30;
      Settings.instance().setTgx101RingRampSeconds(seconds);
      adapter.updateValuedSettingById(R.id.btn_tgx101RingRampTime);
    } else if (id == R.id.btn_lessDataForCalls) {
      final int res = result.get(R.id.btn_lessDataForCalls);
      final @DataSavingOption int option =
        res == R.id.btn_always ? DataSavingOption.ALWAYS :
        res == R.id.btn_mobile ? DataSavingOption.MOBILE :
        res == R.id.btn_roaming ? DataSavingOption.ROAMING :
        DataSavingOption.NEVER;

      if (tdlib.files().setVoipDataSavingOption(option)) {
        adapter.updateValuedSettingById(R.id.btn_lessDataForCalls);
      }
    } else if (id == R.id.btn_inPrivateChats || id == R.id.btn_inGroupChats || id == R.id.btn_inChannelChats || id == R.id.btn_mediaMobileLimits || id == R.id.btn_mediaWiFiLimits || id == R.id.btn_mediaRoamingLimits) {
      int size = 0;
      int flags = 0;
      final int itemCount = result.size();
      for (int i = 0; i < itemCount; i++) {
        int key = result.keyAt(i);
        int value = result.valueAt(i);
        if (key == R.id.btn_size) {
          size = TdlibFilesManager.DOWNLOAD_LIMIT_OPTIONS[value];
        } else if (key == R.id.btn_photos) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_PHOTO;
        } else if (key == R.id.btn_voice) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_VOICE;
        } else if (key == R.id.btn_videoNote) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE;
        } else if (key == R.id.btn_video) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_VIDEO;
        } else if (key == R.id.btn_file) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_FILE;
        } else if (key == R.id.btn_music) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_MUSIC;
        } else if (key == R.id.btn_gif) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_GIF;
        }
      }

      boolean changed = false;
      if (id == R.id.btn_inPrivateChats) {
        changed = tdlib.files().setDownloadInPrivateChats(flags);
      } else if (id == R.id.btn_inGroupChats) {
        changed = tdlib.files().setDownloadInGroupChats(flags);
      } else if (id == R.id.btn_inChannelChats) {
        changed = tdlib.files().setDownloadInChannelChats(flags);
      } else if (id == R.id.btn_mediaMobileLimits) {
        changed = tdlib.files().setLimitsOverMobile(flags, size);
      } else if (id == R.id.btn_mediaWiFiLimits) {
        changed = tdlib.files().setLimitsOverWiFi(flags, size);
      } else if (id == R.id.btn_mediaRoamingLimits) {
        changed = tdlib.files().setLimitsOverRoaming(flags, size);
      }

      if (changed) {
        adapter.updateValuedSettingById(id);
      }
    }
  }

  // TGx101: photo on the call screen

  private static int callPhotoModeName (int mode) {
    switch (mode) {
      case Settings.CALL_PHOTO_FULL_SCREEN: return R.string.Tgx101CallPhotoFullScreen;
      case Settings.CALL_PHOTO_NONE: return R.string.Tgx101CallPhotoNone;
      default: return R.string.Tgx101CallPhotoCircle;
    }
  }

  private static int tapModeName (int mode) {
    switch (mode) {
      case Settings.TAP_MODE_DOUBLE: return R.string.Tgx101TapModeDouble;
      case Settings.TAP_MODE_DOUBLE_SWIPE: return R.string.Tgx101TapModeDoubleSwipe;
      default: return R.string.Tgx101TapModeStock;
    }
  }

  private void showTapModes () {
    final int[] modes = {Settings.TAP_MODE_STOCK, Settings.TAP_MODE_DOUBLE, Settings.TAP_MODE_DOUBLE_SWIPE};
    final int current = Settings.instance().getTapMode();
    ListItem[] items = new ListItem[modes.length];
    for (int i = 0; i < modes.length; i++) {
      items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, modes[i] + 1, 0, tapModeName(modes[i]), R.id.btn_tgx101TapMode, modes[i] == current);
    }
    showSettings(new SettingsWrapBuilder(R.id.btn_tgx101TapMode)
      .setRawItems(items)
      .setIntDelegate((id, result) -> {
        int selected = result.get(R.id.btn_tgx101TapMode);
        if (selected > 0) {
          Settings.instance().setTapMode(selected - 1);
          adapter.updateValuedSettingById(R.id.btn_tgx101TapMode);
        }
      }));
  }

  private static int quickReplyIndex (int id) {
    if (id == R.id.btn_tgx101QuickReply1) return 0;
    if (id == R.id.btn_tgx101QuickReply2) return 1;
    if (id == R.id.btn_tgx101QuickReply3) return 2;
    if (id == R.id.btn_tgx101QuickReply4) return 3;
    if (id == R.id.btn_tgx101QuickReply5) return 4;
    return -1;
  }

  // TGx101: own speech recognition models (MagiX → Speech recognition)

  private final org.thunderdog.challegram.data.Tgx101SpeechModels.Listener tgx101SpeechListener = model -> {
    if (!isDestroyed() && adapter != null) {
      adapter.updateValuedSettingById(R.id.btn_tgx101SpeechModel);
    }
  };

  private String tgx101SpeechModelStatus () {
    for (org.thunderdog.challegram.data.Tgx101SpeechModels.Model model : org.thunderdog.challegram.data.Tgx101SpeechModels.ALL) {
      int percent = org.thunderdog.challegram.data.Tgx101SpeechModels.downloadProgress(model);
      if (percent >= 0) {
        return Lang.getString(R.string.Tgx101SpeechModelDownloading, percent);
      }
    }
    org.thunderdog.challegram.data.Tgx101SpeechModels.Model active = org.thunderdog.challegram.data.Tgx101SpeechModels.active();
    return active != null ? Lang.getString(active.nameRes) : Lang.getString(R.string.Tgx101SpeechModelNone);
  }

  // TGx101: on-device translation dictionaries with their size (user 2026-10-03)
  private void showTgx101TranslateModels () {
    org.thunderdog.challegram.util.Tgx101OnDeviceTranslator.downloadedModels(languages -> {
      if (isDestroyed()) return;
      int size = org.thunderdog.challegram.util.Tgx101OnDeviceTranslator.MODEL_SIZE_MB;
      if (languages.isEmpty()) {
        showOptions(Lang.getString(R.string.Tgx101TranslateModelsNone, size), new int[] {R.id.btn_done}, new String[] {Lang.getString(R.string.OK)}, null, null, (v, id) -> true);
        return;
      }
      int[] ids = new int[languages.size()];
      String[] names = new String[languages.size()];
      int[] icons = new int[languages.size()];
      for (int i = 0; i < ids.length; i++) {
        ids[i] = i + 1;
        names[i] = org.thunderdog.challegram.util.Tgx101OnDeviceTranslator.languageName(languages.get(i)) + " · ≈" + size + " " + Lang.getString(R.string.Tgx101Megabytes) + " · " + Lang.getString(R.string.Tgx101SpeechModelDelete);
        icons[i] = R.drawable.baseline_delete_24;
      }
      showOptions(Lang.getString(R.string.Tgx101TranslateModelsHint, languages.size() * size), ids, names, null, icons, (v, optionId) -> {
        String language = languages.get(optionId - 1);
        org.thunderdog.challegram.util.Tgx101OnDeviceTranslator.deleteModel(language, () -> UI.showToast(R.string.Tgx101TranslateModelDeleted, android.widget.Toast.LENGTH_SHORT));
        return true;
      });
    });
  }

  private void showTgx101SpeechModels () {
    if (!org.thunderdog.challegram.data.Tgx101SpeechModels.isSupported()) {
      return;
    }
    org.thunderdog.challegram.data.Tgx101SpeechModels.Model[] models = org.thunderdog.challegram.data.Tgx101SpeechModels.ALL;
    org.thunderdog.challegram.data.Tgx101SpeechModels.Model active = org.thunderdog.challegram.data.Tgx101SpeechModels.active();
    int[] ids = new int[models.length];
    String[] names = new String[models.length];
    int[] icons = new int[models.length];
    for (int i = 0; i < models.length; i++) {
      org.thunderdog.challegram.data.Tgx101SpeechModels.Model model = models[i];
      ids[i] = i + 1;
      int percent = org.thunderdog.challegram.data.Tgx101SpeechModels.downloadProgress(model);
      String status = percent >= 0 ? Lang.getString(R.string.Tgx101SpeechModelDownloading, percent) :
        model == active ? Lang.getString(R.string.Tgx101SpeechModelInUse) :
        org.thunderdog.challegram.data.Tgx101SpeechModels.isDownloaded(model) ? Lang.getString(R.string.Tgx101SpeechModelUse) :
        Lang.getString(R.string.Tgx101SpeechModelDownload);
      names[i] = Lang.getString(model.nameRes) + " · " + status;
      icons[i] = model == active ? R.drawable.baseline_check_24 : org.thunderdog.challegram.data.Tgx101SpeechModels.isDownloaded(model) ? R.drawable.baseline_mic_24 : R.drawable.baseline_file_download_24;
    }
    showOptions(Lang.getString(R.string.Tgx101SpeechModelHint), ids, names, null, icons, (itemView, optionId) -> {
      org.thunderdog.challegram.data.Tgx101SpeechModels.Model model = models[optionId - 1];
      if (org.thunderdog.challegram.data.Tgx101SpeechModels.downloadProgress(model) >= 0) {
        return true;
      }
      if (!org.thunderdog.challegram.data.Tgx101SpeechModels.isDownloaded(model)) {
        org.thunderdog.challegram.data.Tgx101SpeechModels.download(model);
      } else {
        boolean inUse = model == org.thunderdog.challegram.data.Tgx101SpeechModels.active();
        showOptions(Lang.getString(model.nameRes),
          inUse ? new int[] {R.id.btn_delete} : new int[] {R.id.btn_done, R.id.btn_delete},
          inUse ? new String[] {Lang.getString(R.string.Tgx101SpeechModelDelete)} : new String[] {Lang.getString(R.string.Tgx101SpeechModelUse), Lang.getString(R.string.Tgx101SpeechModelDelete)},
          inUse ? new int[] {OptionColor.RED} : new int[] {OptionColor.NORMAL, OptionColor.RED},
          inUse ? new int[] {R.drawable.baseline_delete_24} : new int[] {R.drawable.baseline_check_24, R.drawable.baseline_delete_24},
          (v, id) -> {
            if (id == R.id.btn_done) {
              org.thunderdog.challegram.data.Tgx101SpeechModels.select(model);
            } else if (id == R.id.btn_delete) {
              org.thunderdog.challegram.data.Tgx101SpeechModels.delete(model);
            }
            return true;
          });
      }
      return true;
    });
  }

  private void showCallPhotoModes () {
    final int[] modes = {Settings.CALL_PHOTO_CIRCLE, Settings.CALL_PHOTO_FULL_SCREEN, Settings.CALL_PHOTO_NONE};
    final int current = Settings.instance().getCallPhotoMode();
    ListItem[] items = new ListItem[modes.length];
    for (int i = 0; i < modes.length; i++) {
      items[i] = new ListItem(ListItem.TYPE_RADIO_OPTION, modes[i] + 1, 0, callPhotoModeName(modes[i]), R.id.btn_tgx101CallPhoto, modes[i] == current);
    }
    showSettings(new SettingsWrapBuilder(R.id.btn_tgx101CallPhoto)
      .setRawItems(items)
      .setIntDelegate((id, result) -> {
        int selected = result.get(R.id.btn_tgx101CallPhoto);
        if (selected > 0) {
          Settings.instance().setCallPhotoMode(selected - 1);
          adapter.updateValuedSettingById(R.id.btn_tgx101CallPhoto);
        }
      }));
  }
}
