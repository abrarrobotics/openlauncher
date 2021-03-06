package com.benny.openlauncher.widget;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.benny.openlauncher.R;
import com.benny.openlauncher.activity.HomeActivity;
import com.benny.openlauncher.manager.Setup;
import com.benny.openlauncher.model.Item;
import com.benny.openlauncher.model.App;
import com.benny.openlauncher.util.AppSettings;
import com.benny.openlauncher.util.Definitions;
import com.benny.openlauncher.util.DragAction;
import com.benny.openlauncher.util.DragHandler;
import com.benny.openlauncher.util.Tool;
import com.benny.openlauncher.viewutil.DesktopCallback;
import com.benny.openlauncher.viewutil.GroupIconDrawable;

import io.codetail.animation.ViewAnimationUtils;
import io.codetail.widget.RevealFrameLayout;

public class GroupPopupView extends RevealFrameLayout {
    private boolean _isShowing;
    private CardView _popupCard;
    private CellContainer _cellContainer;
    private PopupWindow.OnDismissListener _dismissListener;
    private Animator _folderAnimator;
    private int _cx;
    private int _cy;
    private TextView _textView;

    public GroupPopupView(Context context) {
        super(context);
        init();
    }

    public GroupPopupView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        if (isInEditMode()) {
            return;
        }
        _popupCard = (CardView) LayoutInflater.from(getContext()).inflate(R.layout.view_group_popup, this, false);
        // set the CardView color
        int color = Setup.appSettings().getDesktopFolderColor();
        int alpha = Color.alpha(color);
        _popupCard.setCardBackgroundColor(color);
        // remove elevation if CardView's background is transparent to avoid weird shadows because CardView does not support transparent backgrounds
        if (alpha == 0) {
            _popupCard.setCardElevation(0f);
        }
        _cellContainer = _popupCard.findViewById(R.id.group);

        bringToFront();

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (_dismissListener != null) {
                    _dismissListener.onDismiss();
                }
                dismissPopup();
            }
        });

        addView(_popupCard);
        _popupCard.setVisibility(View.INVISIBLE);
        setVisibility(View.INVISIBLE);

        _textView = _popupCard.findViewById(R.id.group_popup_label);
    }


    public boolean showWindowV(final Item item, final View itemView, final DesktopCallback callback) {
        if (_isShowing || getVisibility() == View.VISIBLE) return false;
        _isShowing = true;

        String label = item.getLabel();
        _textView.setVisibility(label.isEmpty() ? GONE : VISIBLE);
        _textView.setText(label);
        _textView.setTextColor(Setup.appSettings().getFolderLabelColor());
        _textView.setTypeface(null, Typeface.BOLD);

        final Context c = itemView.getContext();
        int[] cellSize = GroupPopupView.GroupDef.getCellSize(item.getGroupItems().size());
        _cellContainer.setGridSize(cellSize[0], cellSize[1]);

        int iconSize = Tool.dp2px(Setup.appSettings().getDesktopIconSize(), c);
        int textSize = Tool.dp2px(22, c);
        int contentPadding = Tool.dp2px(6, c);

        for (int x2 = 0; x2 < cellSize[0]; x2++) {
            for (int y2 = 0; y2 < cellSize[1]; y2++) {
                if (y2 * cellSize[0] + x2 > item.getGroupItems().size() - 1) {
                    continue;
                }
                final AppSettings AppSettings = Setup.appSettings();
                final Item groupItem = item.getGroupItems().get(y2 * cellSize[0] + x2);
                if (groupItem == null) {
                    continue;
                }
                final App groupApp = groupItem.getType() != Item.Type.SHORTCUT ? Setup.appLoader().findItemApp(groupItem) : null;
                AppItemView appItemView = AppItemView.createAppItemViewPopup(getContext(), groupItem, groupApp, AppSettings.getDesktopIconSize(), AppSettings.getDrawerLabelFontSize());
                final View view = appItemView.getView();

                view.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view2) {
                        if (Setup.appSettings().isDesktopLock()) return false;

                        removeItem(c, callback, item, groupItem, (AppItemView) itemView);

                        DragAction.Action action = groupItem.getType() == Item.Type.SHORTCUT ? DragAction.Action.SHORTCUT : DragAction.Action.APP;

                        // start the drag action
                        DragHandler.startDrag(view, groupItem, action, null);

                        dismissPopup();
                        updateItem(callback, item, groupItem, itemView);
                        return true;
                    }
                });
                final App app = Setup.appLoader().findItemApp(groupItem);
                if (app == null) {
                    removeItem(c, callback, item, groupItem, (AppItemView) itemView);
                } else {
                    view.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Tool.createScaleInScaleOutAnim(view, new Runnable() {
                                @Override
                                public void run() {
                                    dismissPopup();
                                    setVisibility(View.INVISIBLE);
                                    view.getContext().startActivity(groupItem.getIntent());
                                }
                            }, 1f);
                        }
                    });
                }
                _cellContainer.addViewToGrid(view, x2, y2, 1, 1);
            }
        }

        _dismissListener = new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (((AppItemView) itemView).getCurrentIcon() != null) {
                    ((GroupIconDrawable) ((AppItemView) itemView).getCurrentIcon()).popBack();
                }
            }
        };

        int popupWidth = contentPadding * 8 + _popupCard.getContentPaddingLeft() + _popupCard.getContentPaddingRight() + (iconSize) * cellSize[0];
        _popupCard.getLayoutParams().width = popupWidth;

        int popupHeight = contentPadding * 2 + _popupCard.getContentPaddingTop() + _popupCard.getContentPaddingBottom() + Tool.dp2px(30, c) + (iconSize + textSize) * cellSize[1];
        _popupCard.getLayoutParams().height = popupHeight;

        _cx = popupWidth / 2;
        _cy = popupHeight / 2 - (Setup.appSettings().isDesktopShowLabel() ? Tool.dp2px(10, getContext()) : 0);

        int[] coordinates = new int[2];
        itemView.getLocationInWindow(coordinates);

        coordinates[0] += itemView.getWidth() / 2;
        coordinates[1] += itemView.getHeight() / 2;

        coordinates[0] -= popupWidth / 2;
        coordinates[1] -= popupHeight / 2;

        int width = getWidth();
        int height = getHeight();

        if (coordinates[0] + popupWidth > width) {
            int v = width - (coordinates[0] + popupWidth);
            coordinates[0] += v;
            coordinates[0] -= contentPadding;
            _cx -= v;
            _cx += contentPadding;
        }
        if (coordinates[1] + popupHeight > height) {
            coordinates[1] += height - (coordinates[1] + popupHeight);
        }
        if (coordinates[0] < 0) {
            coordinates[0] -= itemView.getWidth() / 2;
            coordinates[0] += popupWidth / 2;
            coordinates[0] += contentPadding;
            _cx += itemView.getWidth() / 2;
            _cx -= popupWidth / 2;
            _cx -= contentPadding;
        }
        if (coordinates[1] < 0) {
            coordinates[1] -= itemView.getHeight() / 2;
            coordinates[1] += popupHeight / 2;
        }

        if (item.getLocationInLauncher() == Item.LOCATION_DOCK) {
            coordinates[1] -= iconSize / 2;
            _cy += iconSize / 2 + (Setup.appSettings().isDockShowLabel() ? 0 : Tool.dp2px(10, getContext()));
        }

        int x = coordinates[0];
        int y = coordinates[1];

        _popupCard.setPivotX(0);
        _popupCard.setPivotX(0);
        _popupCard.setX(x);
        _popupCard.setY(y);

        setVisibility(View.VISIBLE);
        _popupCard.setVisibility(View.VISIBLE);
        animateFolderOpen();

        return true;
    }

    private void animateFolderOpen() {
        _cellContainer.setAlpha(0);

        int finalRadius = Math.max(_popupCard.getWidth(), _popupCard.getHeight());
        int startRadius = Tool.dp2px(Setup.appSettings().getDesktopIconSize() / 2, getContext());

        long animDuration = 1 + (long) (210 * Setup.appSettings().getOverallAnimationSpeedModifier());
        _folderAnimator = ViewAnimationUtils.createCircularReveal(_popupCard, _cx, _cy, startRadius, finalRadius);
        _folderAnimator.setStartDelay(0);
        _folderAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        _folderAnimator.setDuration(animDuration);
        _folderAnimator.start();
        Tool.visibleViews(animDuration, animDuration, _cellContainer);
    }

    public void dismissPopup() {
        if (!_isShowing) return;
        if (_folderAnimator == null || _folderAnimator.isRunning())
            return;

        long animDuration = 1 + (long) (210 * Setup.appSettings().getOverallAnimationSpeedModifier());
        Tool.invisibleViews(animDuration, _cellContainer);

        int startRadius = Tool.dp2px(Setup.appSettings().getDesktopIconSize() / 2, getContext());
        int finalRadius = Math.max(_popupCard.getWidth(), _popupCard.getHeight());
        _folderAnimator = ViewAnimationUtils.createCircularReveal(_popupCard, _cx, _cy, finalRadius, startRadius);
        _folderAnimator.setStartDelay(1 + animDuration / 2);
        _folderAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        _folderAnimator.setDuration(animDuration);
        _folderAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator p1) {
            }

            @Override
            public void onAnimationEnd(Animator p1) {
                _popupCard.setVisibility(View.INVISIBLE);
                _isShowing = false;

                if (_dismissListener != null) {
                    _dismissListener.onDismiss();
                }

                _cellContainer.removeAllViews();
                setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator p1) {
            }

            @Override
            public void onAnimationRepeat(Animator p1) {
            }
        });
        _folderAnimator.start();
    }

    private void removeItem(Context context, final DesktopCallback callback, final Item currentItem, Item dragOutItem, AppItemView currentView) {
        currentItem.getGroupItems().remove(dragOutItem);

        HomeActivity._db.saveItem(dragOutItem, Definitions.ItemState.Visible);
        HomeActivity._db.saveItem(currentItem);

        currentView.setCurrentIcon(new GroupIconDrawable(context, currentItem, Setup.appSettings().getDesktopIconSize()));
    }

    public void updateItem(final DesktopCallback callback, final Item currentItem, Item dragOutItem, View currentView) {
        if (currentItem.getGroupItems().size() == 1) {
            final App app = Setup.appLoader().findItemApp(currentItem.getGroupItems().get(0));
            if (app != null) {
                //Creating a new app item fixed the folder crash bug
                //Home.Companion.getDb().getItem(currentItem.getGroupItems().get(0).getId());
                Item item = Item.newAppItem(app);

                item.setX(currentItem.getX());
                item.setY(currentItem.getY());

                HomeActivity._db.saveItem(item);
                HomeActivity._db.saveItem(item, Definitions.ItemState.Visible);
                HomeActivity._db.deleteItem(currentItem, true);

                callback.removeItem(currentView, false);
                callback.addItemToCell(item, item.getX(), item.getY());
            }
            if (HomeActivity.Companion.getLauncher() != null) {
                HomeActivity.Companion.getLauncher().getDesktop().requestLayout();
            }
        }
    }

    static class GroupDef {
        static int _maxItem = 12;

        static int[] getCellSize(int count) {
            if (count <= 1)
                return new int[]{1, 1};
            if (count <= 2)
                return new int[]{2, 1};
            if (count <= 4)
                return new int[]{2, 2};
            if (count <= 6)
                return new int[]{3, 2};
            if (count <= 9)
                return new int[]{3, 3};
            if (count <= 12)
                return new int[]{4, 3};
            return new int[]{0, 0};
        }
    }
}
