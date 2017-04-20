package com.medxnote.securesms;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.medxnote.securesms.database.model.Cell;
import com.medxnote.securesms.util.ResUtil;

import java.util.HashMap;


public class ConversationMenuRecordItem
        extends TextView implements BindableConversationMenuItem {

    private static final String TAG = ConversationMenuRecordItem.class.getSimpleName();

    public static final int HEIGHT = LinearLayout.LayoutParams.MATCH_PARENT;
    public static final int WIDTH = LinearLayout.LayoutParams.MATCH_PARENT;
    public static final int MARGIN = 5;

    private static final int TEXT_SIZE = 18;

    private Cell cell;

    public ConversationMenuRecordItem(Context context) {
        super(context);

        setTextSize(TEXT_SIZE);
        setGravity(Gravity.CENTER);
    }

    public ConversationMenuRecordItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        setTextSize(TEXT_SIZE);
        setGravity(Gravity.CENTER);
    }

    @Override
    public void bind(Cell cell) {
        this.cell = cell;

        setText(cell.getTitle());

        HashMap<String, String> styles = cell.getStyle();
        if (styles != null && !styles.isEmpty()) {
            setTextColor(ResUtil.getColor(getContext(), styles.get("color")));
            Drawable drawable = ResUtil.getDrawable(GradientDrawable.RECTANGLE,
                    ResUtil.getColor(getContext(), styles.get("bg_color")),
                    5/* radius */, ResUtil.getColor(getContext(), styles.get("border")), 2/* stroke width */);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                setBackground(drawable);
            } else {
                setBackgroundDrawable(drawable);
            }
        } else {
            Log.d(TAG, "bind: Cell " + cell.getCmd() + " doesn't have styles. ");
        }
    }

    @Override
    public void unbind() {
        if (cell != null) {
            cell = null;
        }
    }

    public Cell getCell() {
        return cell;
    }

}
