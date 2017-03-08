package com.medxnote.securesms;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;


public class ConversationMenuView extends LinearLayout {

    private Long menuId;
    private Boolean hasInput;

    public ConversationMenuView(Context context) {
        super(context);

        setOrientation(VERTICAL);
        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public ConversationMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOrientation(VERTICAL);
        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setRowsParams() {
        int count = getChildCount();
        int last = count - 1;
        for (int i = 0; i < count; i++) {
            ConversationMenuRow row = (ConversationMenuRow) getChildAt(i);
            LayoutParams params = new LayoutParams(ConversationMenuRecordItem.WIDTH,
                    row.getRowHeight());
            if (i == last) {
                params.setMargins(ConversationMenuRecordItem.MARGIN,
                        ConversationMenuRecordItem.MARGIN,
                        ConversationMenuRecordItem.MARGIN,
                        ConversationMenuRecordItem.MARGIN);
            } else {
                params.setMargins(ConversationMenuRecordItem.MARGIN,
                        ConversationMenuRecordItem.MARGIN,
                        ConversationMenuRecordItem.MARGIN,
                        0);
            }
            row.setLayoutParams(params);
        }
    }

    public Boolean hasInput() {
        return hasInput;
    }

    public void setInput(Boolean hasInput) {
        this.hasInput = hasInput;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

}