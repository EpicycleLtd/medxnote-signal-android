package com.medxnote.securesms;

import com.medxnote.securesms.database.model.Cell;

public interface BindableConversationMenuItem extends Unbindable {
    void bind(Cell cell);
}
