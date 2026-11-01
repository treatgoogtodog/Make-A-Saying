package com.makeasaying.editor.map.dialogs;

/**
 * Pure state model for save confirmation and error warnings per M6a brief.
 * Refuses to save on ERROR validator findings unless explicitly confirmed.
 */
public final class SaveConfirmModel {
    public enum PromptType {
        NONE,
        CONFIRM_SAVE_WITH_ERRORS,
        CONFIRM_DISCARD_DIRTY_CHANGES
    }

    private PromptType currentPrompt = PromptType.NONE;
    private int errorCount = 0;
    private Runnable onConfirmAction;
    private Runnable onCancelAction;

    public PromptType getCurrentPrompt() {
        return currentPrompt;
    }

    public boolean isOpen() {
        return currentPrompt != PromptType.NONE;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void promptSaveWithErrors(int errorCount, Runnable onConfirm, Runnable onCancel) {
        this.currentPrompt = PromptType.CONFIRM_SAVE_WITH_ERRORS;
        this.errorCount = errorCount;
        this.onConfirmAction = onConfirm;
        this.onCancelAction = onCancel;
    }

    public void promptDiscardDirtyChanges(Runnable onConfirm, Runnable onCancel) {
        this.currentPrompt = PromptType.CONFIRM_DISCARD_DIRTY_CHANGES;
        this.errorCount = 0;
        this.onConfirmAction = onConfirm;
        this.onCancelAction = onCancel;
    }

    public void confirm() {
        Runnable action = onConfirmAction;
        dismiss();
        if (action != null) {
            action.run();
        }
    }

    public void cancel() {
        Runnable action = onCancelAction;
        dismiss();
        if (action != null) {
            action.run();
        }
    }

    public void dismiss() {
        this.currentPrompt = PromptType.NONE;
        this.errorCount = 0;
        this.onConfirmAction = null;
        this.onCancelAction = null;
    }
}
