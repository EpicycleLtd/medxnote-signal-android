package com.medxnote.securesms;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;

import com.medxnote.securesms.database.model.Cell;
import com.medxnote.securesms.database.model.MenuRecord;
import com.medxnote.securesms.database.model.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class ConversationMenuAdapter {

    private static final String TAG = ConversationMenuAdapter.class.getSimpleName();

    private Context context;
    private MenuClickListener listener;
    private List<ConversationMenuView> menus;

    public ConversationMenuAdapter(Context context) {
        this.context = context;
    }

    public void setRecords(List<MenuRecord> records) {
        this.menus = setMenuViews(records);
    }

    public ConversationMenuView getConversationMenuView(Long id) {
        if (!id.equals(-1L) && menus != null) {
            for (ConversationMenuView menuView : menus) {
                if (menuView.getMenuId().equals(id)) {
                    return menuView;
                }
            }
        }
        return null;
    }

    private ConversationMenuRecordItem addCell(Cell cell) {
        final ConversationMenuRecordItem menuView = new ConversationMenuRecordItem(context);
            menuView.bind(cell);
            menuView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) listener.onMenuClick(menuView);
                }
            });
        return menuView;
    }

    private ConversationMenuRow addRow(Row row) {
        ConversationMenuRow rowView = new ConversationMenuRow(context);
        HashMap<String, String> style = row.getStyle();
        List<Cell> cells = row.getCells();
        if (cells != null) {
            if (!cells.isEmpty()) {
                int length = cells.size();
                rowView.setLength(length);
                if (style != null && !style.isEmpty()) {
                    rowView.setRowHeight(style.get("size") == null
                            ? 1 : Double.parseDouble(style.get("size")));
                }
                for (Cell cell : cells) {
                    rowView.addView(addCell(cell));
                }
                rowView.setCellsParams();
            } else {
                Log.e(TAG, "addRow: Cells are empty. ");
            }
        } else {
            Log.e(TAG, "addRow: Cells are NULL. ");
        }
        return rowView;
    }

    private ConversationMenuView addMenu(MenuRecord record) {
        if (record != null) {
            ConversationMenuView menu = new ConversationMenuView(context);
                menu.setMenuId(record.getId());

                HashMap<String, String> style = record.getStyle();
                if (style != null && !style.isEmpty()) {
                    String background = style.get("bg_color");
                    if (background != null && !background.isEmpty()) {
                        menu.setBackgroundColor(Color.parseColor(background));
                    } else {
                        menu.setBackgroundColor(context.getResources().getColor(android.R.color.transparent));
                    }
                }

                menu.setInput(record.hasInput());

                List<Row> rows = record.getRows();
                if (rows != null) {
                    if (!rows.isEmpty()) {
                        for (Row row : rows) {
                            menu.addView(addRow(row));
                        }
                        menu.setRowsParams();
                    } else {
                        Log.e(TAG, "addMenu: Rows are empty. ");
                    }
                } else {
                    Log.e(TAG, "addMenu: Rows are NULL. ");
                }
            return menu;
        }
        return null;
    }

    private List<ConversationMenuView> setMenuViews(List<MenuRecord> records) {
        if (records != null) {
            if (!records.isEmpty()) {
                List<ConversationMenuView> mConversationMenuViews = new ArrayList<>();
                for (MenuRecord record : records) {
                    mConversationMenuViews.add(addMenu(record));
                }
                return mConversationMenuViews;
            } else {
                Log.e(TAG, "addMenuView: Records are empty. ");
            }
        } else {
            Log.e(TAG, "addMenuView: You should set list of menus records, before start work with adapter. ");
        }

        return null;
    }

    public List<ConversationMenuView> getConversationMenuView() {
        return menus;
    }

    public void setMenuClickListener(MenuClickListener listener) {
        this.listener = listener;
    }

    interface MenuClickListener {
        void onMenuClick(ConversationMenuRecordItem item);
    }

}
