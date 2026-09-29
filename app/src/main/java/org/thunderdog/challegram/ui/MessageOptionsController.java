package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiPacksInfoView;
import org.thunderdog.challegram.widget.EmojiTextView;

import me.vkryl.core.StringUtils;

public class MessageOptionsController extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<MessageOptionsController.Args> {
  private Options options;
  private long[] emojiPackIds;
  private long emojiPackFirstEmoji;
  private View.OnClickListener listener;
  private OptionsAdapter adapter;
  private ThemeListenerList themeProvider;
  private Runnable hideWindowDelegate;


  public MessageOptionsController (Context context, Tdlib tdlib, ThemeListenerList themeProvider) {
    super(context, tdlib);
    this.themeProvider = themeProvider;
  }

  @Override
  public void setArguments (MessageOptionsController.Args args) {
    super.setArguments(args);
    this.options = args.options;
    this.listener = args.listener;
    this.emojiPackIds = args.emojiPackIds;
    this.emojiPackFirstEmoji = args.emojiPackFirstEmoji;
    this.hideWindowDelegate = args.hideWindowDelegate;
  }

  @Override
  public boolean needBottomDecorationOffsets (RecyclerView parent) {
    return false;
  }

  @Override
  public int getId () {
    return R.id.controller_messageOptions;
  }

  @Override
  protected int getRecyclerBackground () {
    return ColorId.filling;
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new OptionsAdapter(context, this, options, emojiPackFirstEmoji, emojiPackIds, listener, themeProvider);
    LinearLayoutManager manager = new LinearLayoutManager(context);
    addThemeInvalidateListener(recyclerView);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);
    recyclerView.setItemAnimator(null);
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  public static class Args {
    public Options options;
    public View.OnClickListener listener;
    public long[] emojiPackIds;
    public long emojiPackFirstEmoji;
    public Runnable hideWindowDelegate;

    public Args (Options options, View.OnClickListener listener, long emojiPackFirstEmoji, long[] emojiPackIds, Runnable hideWindowDelegate) {
      this.options = options;
      this.listener = listener;
      this.emojiPackIds = emojiPackIds;
      this.emojiPackFirstEmoji = emojiPackFirstEmoji;
      this.hideWindowDelegate = hideWindowDelegate;
    }
  }


  /** TGx101: height of the option rows (icon row + list), for the sheet position */
  public static int getOptionRowsHeight (OptionItem[] items) {
    if (items == null) {
      return 0;
    }
    OptionItem[][] split = OptionsAdapter.split(items);
    return split[1].length * Screen.dp(OPTION_HEIGHT_DP) + (split[0].length > 0 ? Screen.dp(QUICK_BAR_HEIGHT_DP) : 0);
  }

  // TGx101: shorter menu (MagiX redesign, variant 1)
  private static final float OPTION_HEIGHT_DP = 48f;
  private static final float QUICK_BAR_HEIGHT_DP = 72f;

  private static class OptionHolder extends RecyclerView.ViewHolder {
    public OptionHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static OptionHolder create (Context context, ViewController<?> parent, int viewType, View.OnClickListener onClickListener) {
      if (viewType == OptionsAdapter.TYPE_QUICK_BAR) {
        // TGx101: the most used actions as one row of icon buttons
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Screen.dp(6f), Screen.dp(4f), Screen.dp(6f), Screen.dp(4f));
        bar.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(QUICK_BAR_HEIGHT_DP)));
        for (int i = 0; i < OptionsAdapter.QUICK_BAR_MAX; i++) {
          TextView button = new TextView(context);
          button.setTypeface(Fonts.getRobotoRegular());
          button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
          button.setGravity(Gravity.CENTER);
          button.setSingleLine(true);
          button.setEllipsize(TextUtils.TruncateAt.END);
          button.setCompoundDrawablePadding(Screen.dp(6f));
          button.setPadding(Screen.dp(2f), Screen.dp(8f), Screen.dp(2f), Screen.dp(6f));
          button.setOnClickListener(onClickListener);
          Views.setClickable(button);
          RippleSupport.setTransparentSelector(button);
          bar.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return new OptionHolder(bar);
      }
      if (viewType == OptionsAdapter.TYPE_OPTION) {
        EmojiTextView text = new EmojiTextView(context);
        text.setScrollDisabled(true);
        text.setTypeface(Fonts.getRobotoRegular());
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        text.setOnClickListener(onClickListener);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        text.setPadding(Screen.dp(17f), Screen.dp(1f), Screen.dp(17f), 0);
        text.setCompoundDrawablePadding(Screen.dp(18f));
        text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(OPTION_HEIGHT_DP))); // TGx101: 54 → 48 dp
        Views.setClickable(text);
        RippleSupport.setTransparentSelector(text);
        return new OptionHolder(text);
      } else if (viewType == OptionsAdapter.TYPE_EMOJI_PACK_INFO) {
        EmojiPacksInfoView textView = new EmojiPacksInfoView(context, parent, parent.tdlib());
        return new OptionHolder(textView);
      } else if (viewType == OptionsAdapter.TYPE_SUBTITLE) {
        EmojiTextView textView = OptionsLayout.genSubtitle(context);
        return new OptionHolder(textView);
      } else {
        CustomTextView textView = new CustomTextView(context, parent.tdlib());
        textView.setTextColorId(ColorId.textLight);
        textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(6f));
        return new OptionHolder(textView);
      }
    }
  }

  private static class OptionsAdapter extends RecyclerView.Adapter<OptionHolder> {
    private final Context context;
    private final View.OnClickListener onClickListener;
    private Options options;
    private final Tdlib tdlib;
    private final MessageOptionsController parent;
    @Nullable
    private final ThemeListenerList themeProvider;
    private final long[] emojiPackIds;
    private final long emojiPackFirstEmoji;

    private int textInfoPosition, emojiInfoPosition, subtitlePosition;

    public void updateSubtitle (Options options) {
      this.options = options;
      int prevSubtitlePosition = this.subtitlePosition;
      boolean hadSubtitle = prevSubtitlePosition >= 0;
      boolean hasSubtitle = options.subtitle != null;
      if (hadSubtitle != hasSubtitle) {
        if (hadSubtitle) {
          subtitlePosition = -1;
          if (textInfoPosition >= 0) {
            textInfoPosition--;
          }
          if (emojiInfoPosition >= 0) {
            emojiInfoPosition--;
          }
          notifyItemRemoved(prevSubtitlePosition);
        } else {
          subtitlePosition = 0;
          if (textInfoPosition >= 0) {
            textInfoPosition++;
          }
          if (emojiInfoPosition >= 0) {
            emojiInfoPosition++;
          }
          notifyItemInserted(subtitlePosition);
        }
      } else if (hasSubtitle) {
        notifyItemChanged(subtitlePosition);
      }
    }

    public static final int TYPE_OPTION = 0;
    public static final int TYPE_INFO = 1;
    public static final int TYPE_EMOJI_PACK_INFO = 2;
    public static final int TYPE_SUBTITLE = 3;
    public static final int TYPE_QUICK_BAR = 4;
    public static final int QUICK_BAR_MAX = 4;

    // TGx101: actions shown in the icon row, and the rest shown as the list
    private OptionItem[] barItems = new OptionItem[0];
    private OptionItem[] listItems;

    private void splitItems () {
      OptionItem[][] split = split(options.items);
      barItems = split[0];
      listItems = split[1];
    }

    static OptionItem[][] split (OptionItem[] all) {
      int[] candidates = {R.id.btn_messageReply, R.id.btn_messageCopy, R.id.btn_messageEdit, R.id.btn_messageShare};
      OptionItem delete = null;
      java.util.ArrayList<OptionItem> bar = new java.util.ArrayList<>();
      for (OptionItem item : all) {
        if (item.id == R.id.btn_messageDelete) {
          delete = item;
        }
      }
      int limit = delete != null ? QUICK_BAR_MAX - 1 : QUICK_BAR_MAX;
      for (int candidate : candidates) {
        if (bar.size() >= limit) break;
        for (OptionItem item : all) {
          if (item.id == candidate) {
            bar.add(item);
            break;
          }
        }
      }
      if (delete != null) {
        bar.add(delete);
      }
      if (bar.size() < 3) {
        return new OptionItem[][] {new OptionItem[0], all};
      }
      java.util.ArrayList<OptionItem> rest = new java.util.ArrayList<>();
      for (OptionItem item : all) {
        if (!bar.contains(item)) {
          rest.add(item);
        }
      }
      return new OptionItem[][] {bar.toArray(new OptionItem[0]), rest.toArray(new OptionItem[0])};
    }

    private int quickBarPosition () {
      if (barItems.length == 0) {
        return -1;
      }
      return Math.max(textInfoPosition, Math.max(emojiInfoPosition, subtitlePosition)) + 1;
    }

    OptionsAdapter (Context context, MessageOptionsController parent, Options options, long emojiPackFirstEmoji, long[] emojiPackIds, View.OnClickListener onClickListener, @Nullable ThemeListenerList themeProvider) {
      this.parent = parent;
      this.tdlib = parent.tdlib();
      this.onClickListener = onClickListener;
      this.context = context;
      this.options = options;
      this.themeProvider = themeProvider;
      this.emojiPackIds = emojiPackIds;
      this.emojiPackFirstEmoji = emojiPackFirstEmoji;

      this.subtitlePosition = options.subtitle != null ? 0 : -1;
      this.emojiInfoPosition = emojiPackIds.length > 0 ? (subtitlePosition + 1) : -1;
      this.textInfoPosition = StringUtils.isEmpty(options.info) ? -1 : (Math.max(emojiInfoPosition, subtitlePosition) + 1);
      splitItems();
    }

    @NonNull
    @Override
    public OptionHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return OptionHolder.create(context, this.parent, viewType, onClickListener);
    }

    @Override
    public void onBindViewHolder (@NonNull OptionHolder holder, int position) {
      int type = getItemViewType(position);
      switch (type) {
        case TYPE_QUICK_BAR: {
          ViewGroup bar = (ViewGroup) holder.itemView;
          for (int i = 0; i < bar.getChildCount(); i++) {
            TextView button = (TextView) bar.getChildAt(i);
            if (i >= barItems.length) {
              button.setVisibility(View.GONE);
              continue;
            }
            OptionItem item = barItems[i];
            button.setVisibility(View.VISIBLE);
            button.setId(item.id);
            final boolean isDelete = item.id == R.id.btn_messageDelete;
            final int textColorId = isDelete ? ColorId.textNegative : OptionsLayout.getOptionColorId(item.textColor);
            button.setTextColor(Theme.getColor(textColorId));
            if (themeProvider != null)
              themeProvider.addThemeColorListener(button, textColorId);
            Drawable drawable = item.icon != 0 ? Drawables.get(context.getResources(), item.icon).mutate() : null;
            if (drawable != null) {
              final int drawableColorId = isDelete ? ColorId.iconNegative : item.iconColor == OptionColor.NORMAL ? ColorId.icon : item.iconColor;
              drawable.setColorFilter(Paints.getColorFilter(Theme.getColor(drawableColorId)));
              if (themeProvider != null) {
                themeProvider.addThemeFilterListener(drawable, drawableColorId);
              }
            }
            button.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            button.setText(item.name);
          }
          break;
        }
        case TYPE_OPTION: {
          if (subtitlePosition >= 0) {
            position--;
          }
          if (emojiInfoPosition >= 0) {
            position--;
          }
          if (textInfoPosition >= 0) {
            position--;
          }
          if (quickBarPosition() >= 0) {
            position--;
          }
          OptionItem item = listItems[position];
          TextView textView = ((TextView) holder.itemView);
          textView.setId(item.id);
          final int textColorId = OptionsLayout.getOptionColorId(item.textColor);
          textView.setTextColor(Theme.getColor(textColorId));
          if (themeProvider != null)
            themeProvider.addThemeColorListener(textView, textColorId);
          if (item.icon != 0) {
            Drawable drawable = Drawables.get(context.getResources(), item.icon);
            if (drawable != null) {
              final int drawableColorId = item.iconColor == OptionColor.NORMAL ? ColorId.icon : item.iconColor;
              drawable.setColorFilter(Paints.getColorFilter(Theme.getColor(drawableColorId)));
              if (themeProvider != null) {
                themeProvider.addThemeFilterListener(drawable, drawableColorId);
              }
              if (Lang.rtl()) {
                textView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
              } else {
                textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
              }
            }
          } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
          }
          textView.setText(item.name);
          break;
        }
        case TYPE_INFO: {
          CustomTextView textView = ((CustomTextView) holder.itemView);
          String str = options.info.toString();
          TextEntity[] parsed = TD.collectAllEntities(parent, tdlib, options.info, false, null);
          textView.setTextSize(15f);
          textView.setTextColorId(ColorId.textLight);
          textView.setText(str, parsed, false);
          break;
        }
        case TYPE_EMOJI_PACK_INFO: {
          EmojiPacksInfoView textView = ((EmojiPacksInfoView) holder.itemView);
          textView.setId(R.id.btn_emojiPackInfoButton);
          textView.setTextSize(15f);
          textView.setTextColorId(ColorId.textLight);
          textView.update(emojiPackFirstEmoji, emojiPackIds, new ClickableSpan() {
            @Override
            public void onClick (@NonNull View widget) {
              parent.listener.onClick(textView);
            }
          }, false);
          break;
        }
        case TYPE_SUBTITLE: {
          OptionItem item = options.subtitle;
          OptionsLayout.updateSubtitle((EmojiTextView) holder.itemView, item.name, item.icon, item.textColor, item.iconColor, null, parent);
          break;
        }
        default: {
          throw new IllegalStateException(Integer.toString(type));
        }
      }
    }

    @Override
    public int getItemViewType (int position) {
      if (position == quickBarPosition()) {
        return TYPE_QUICK_BAR;
      }
      if (position == textInfoPosition) {
        return TYPE_INFO;
      }
      if (position == emojiInfoPosition) {
        return TYPE_EMOJI_PACK_INFO;
      }
      if (position == subtitlePosition) {
        return TYPE_SUBTITLE;
      }
      return TYPE_OPTION;
    }

    @Override
    public int getItemCount () {
      int itemCount = listItems.length + (barItems.length > 0 ? 1 : 0);
      if (textInfoPosition >= 0) {
        itemCount++;
      }
      if (emojiInfoPosition >= 0) {
        itemCount++;
      }
      if (subtitlePosition >= 0) {
        itemCount++;
      }
      return itemCount;
    }
  }

  public void updateSubtitle (Options options) {
    this.options = options;
    adapter.updateSubtitle(options);
  }

  @Override
  public int getItemsHeight (RecyclerView recyclerView) {
    int totalHeight = 2 * Screen.dp(54) + getOptionRowsHeight(options.items); // TGx101
    if (adapter.textInfoPosition >= 0) {
      View view = recyclerView.getLayoutManager().findViewByPosition(adapter.textInfoPosition);
      int hintHeight =
        view instanceof CustomTextView && ((CustomTextView) view).checkMeasuredWidth(recyclerView.getMeasuredWidth()) ?
          view.getMeasuredHeight() : 0;
      if (hintHeight > 0) {
        totalHeight += hintHeight;
      } else {
        int availWidth = recyclerView.getMeasuredWidth() - Screen.dp(16f) * 2;
        if (availWidth > 0) {
          totalHeight += CustomTextView.measureHeight(this, options.info, 0, 15f, availWidth) + Screen.dp(14f) + Screen.dp(6f);
        } else {
          totalHeight += Screen.dp(14f) + Screen.dp(6f) + Screen.dp(15f);
        }
      }
    }
    if (adapter.emojiInfoPosition >= 0) {
      totalHeight += Screen.dp(40);
    }
    if (adapter.subtitlePosition >= 0) {
      View view = recyclerView.getLayoutManager().findViewByPosition(adapter.subtitlePosition);
      int height = view != null ? view.getMeasuredHeight() : 0;
      totalHeight += height != 0 ? height : Screen.dp(40f);
    }
    return totalHeight;
  }
}
