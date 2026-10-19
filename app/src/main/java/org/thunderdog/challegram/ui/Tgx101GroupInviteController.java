package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.voip.Tgx101GroupInvite;
import org.thunderdog.challegram.widget.AvatarView;

/**
 * TGx101 (user 2026-10-19 «делай групповые звонки до конца»): the incoming screen of an invitation to a group call — the
 * same call background and incoming controls as a call (accept / decline, silence, quick replies), the inviter's avatar
 * and name, «invites you to a group call» and who is already in it.
 */
public class Tgx101GroupInviteController extends ViewController<Void> {
  private static final java.util.List<Tgx101GroupInviteController> shown = new java.util.ArrayList<>();

  /** shows the ringing invitation's screen (once) */
  public static void open (BaseActivity activity) {
    Tgx101GroupInvite invite = Tgx101GroupInvite.current();
    if (invite == null || activity.navigation() == null) return;
    if (activity.navigation().getCurrentStackItem() instanceof Tgx101GroupInviteController) return;
    activity.navigation().navigateTo(new Tgx101GroupInviteController(activity, invite.tdlib));
  }

  /** the invitation ended (answered, declined, missed, timed out): its screens go */
  public static void closeAll () {
    for (Tgx101GroupInviteController c : new java.util.ArrayList<>(shown)) {
      if (!c.isDestroyed() && c.navigationController() != null && c.navigationController().getCurrentStackItem() == c) {
        c.navigateBack();
      }
    }
  }

  private final Tgx101GroupInvite invite;

  public Tgx101GroupInviteController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    this.invite = Tgx101GroupInvite.current();
  }

  @Override
  public int getId () {
    return R.id.controller_tgx101GroupInvite;
  }

  @Override
  protected boolean usePopupMode () {
    return true;
  }

  @Override
  protected int getPopupRestoreColor () {
    return 0xff000000;
  }

  @Override
  protected View onCreateView (Context context) {
    shown.add(this);
    FrameLayout root = new FrameLayout(context);
    root.addView(new Tgx101CallBackground(context), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setGravity(Gravity.CENTER_HORIZONTAL);
    column.setPadding(Screen.dp(24f), Screen.getStatusBarHeight() + Screen.dp(64f), Screen.dp(24f), 0);
    root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    if (invite != null) {
      TextView kind = text(context, 15f, 0xccffffff, false);
      kind.setText(Lang.getString(invite.isVideo ? R.string.Tgx101GroupInviteVideoTitle : R.string.Tgx101GroupInviteTitle));
      column.addView(kind);

      AvatarView avatar = new AvatarView(context);
      avatar.setMessageSender(tdlib, invite.message.senderId);
      LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(Screen.dp(120f), Screen.dp(120f));
      ap.topMargin = Screen.dp(28f);
      column.addView(avatar, ap);

      TextView name = text(context, 26f, 0xffffffff, true);
      name.setText(invite.inviterName());
      LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      np.topMargin = Screen.dp(20f);
      column.addView(name, np);

      TextView sub = text(context, 15f, 0xccffffff, false);
      sub.setText(Lang.getString(invite.isVideo ? R.string.Tgx101GroupInviteVideoText : R.string.Tgx101GroupInviteText));
      LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      sp.topMargin = Screen.dp(6f);
      column.addView(sub, sp);

      if (invite.others.length > 0) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < invite.others.length && i < 3; i++) {
          if (names.length() > 0) names.append(", ");
          names.append(tdlib.senderName(invite.others[i], true));
        }
        if (invite.others.length > 3) names.append(" +").append(invite.others.length - 3);
        TextView others = text(context, 14f, 0x99ffffff, false);
        others.setText(Lang.getString(R.string.Tgx101GroupInviteOthers, names.toString()));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.topMargin = Screen.dp(14f);
        column.addView(others, op);
      }
    }

    Tgx101IncomingControls controls = new Tgx101IncomingControls(context, new Tgx101IncomingControls.Callback() {
      @Override
      public void onAnswer () {
        Tgx101GroupInvite i = Tgx101GroupInvite.current();
        if (i != null) i.accept(context());
        else navigateBack();
      }

      @Override
      public void onDecline () {
        Tgx101GroupInvite i = Tgx101GroupInvite.current();
        if (i != null) i.decline();
        else navigateBack();
      }

      @Override
      public void onSilence () {
        Tgx101GroupInvite i = Tgx101GroupInvite.current();
        if (i != null) i.silence();
      }

      @Override
      public void onQuickReply (String text) {
        Tgx101GroupInvite i = Tgx101GroupInvite.current();
        if (i != null) i.declineWithText(text);
        else navigateBack();
      }
    });
    controls.setPadding(0, 0, 0, Screen.getNavigationBarHeight());
    root.addView(controls, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return root;
  }

  private static TextView text (Context context, float sizeDp, int color, boolean bold) {
    TextView view = new TextView(context);
    view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
    view.setTextColor(color);
    view.setTypeface(bold ? Fonts.getRobotoMedium() : Fonts.getRobotoRegular());
    view.setGravity(Gravity.CENTER_HORIZONTAL);
    view.setMaxLines(2);
    view.setEllipsize(TextUtils.TruncateAt.END);
    return view;
  }

  @Override
  public void destroy () {
    super.destroy();
    shown.remove(this);
  }
}
