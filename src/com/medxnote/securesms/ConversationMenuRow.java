package com.medxnote.securesms;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.medxnote.securesms.database.model.Cell;
import com.medxnote.securesms.util.Conversions;

import java.util.HashMap;


public class ConversationMenuRow extends LinearLayout {

    private Integer rowHeight = 50;
    private Integer length;

    public ConversationMenuRow(Context context) {
        super(context);

        setOrientation(HORIZONTAL);
    }

    public ConversationMenuRow(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOrientation(HORIZONTAL);
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public void setRowHeight(Double multiplier) {
        rowHeight = convertToPx(multiplier);
    }

    public Integer getRowHeight() {
        return rowHeight;
    }

    public void setCellsParams() {
        int count = getChildCount();
        int last = count - 1;
        for (int i = 0; i < getChildCount(); i++) {
            ConversationMenuRecordItem child = (ConversationMenuRecordItem) getChildAt(i);
            LayoutParams params = new LayoutParams(ConversationMenuRecordItem.WIDTH, ConversationMenuRecordItem.HEIGHT);
            Cell cell = child.getCell();
            HashMap<String, String> style = cell.getStyle();
            if (style != null && !style.isEmpty()) {
                String width = style.get("width");
                if (width != null && !width.isEmpty()) {
                    // inverse value
                    Integer w = 100 - Integer.valueOf(width);
                    // calculate weight
                    params.weight = ((float) getLength() / 100) * w;
                } else {
                    params.weight = getLength();
                }
            }
            if (i != last) {
                params.setMargins(0, 0, ConversationMenuRecordItem.MARGIN, 0);
            }
            child.setLayoutParams(params);
        }
    }

    private int setMultiplier(Double multiplier) {
        return Double.valueOf(rowHeight * multiplier).intValue();
    }

    private int convertToPx(Double multiplier) {
        int multiplied = setMultiplier(multiplier);
        return Conversions.dpToPx(getContext(), multiplied);
    }

}
