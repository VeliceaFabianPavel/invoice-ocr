package com.invoiceocr.support;

import com.invoiceocr.ui.InvoiceView;
import com.invoiceocr.ui.InvoiceViewListener;
import java.util.ArrayList;
import java.util.List;

/** Fake view that records what it was told to display. */
public final class RecordingInvoiceView implements InvoiceView {

    private final List<String> statuses = new ArrayList<>();
    private final List<Boolean> busyStates = new ArrayList<>();
    private final List<Boolean> exportStates = new ArrayList<>();
    private String rawText = "";
    private String report = "";
    private int clearCount;
    private boolean displayed;

    @Override
    public void addListener(InvoiceViewListener listener) {
        // the presenter under test is invoked directly
    }

    @Override
    public void showRawText(String text) {
        this.rawText = text;
    }

    @Override
    public void showReport(String text) {
        this.report = text;
    }

    @Override
    public void showStatus(String status) {
        statuses.add(status);
    }

    @Override
    public void setBusy(boolean busy) {
        busyStates.add(busy);
    }

    @Override
    public void setExportEnabled(boolean enabled) {
        exportStates.add(enabled);
    }

    public List<Boolean> exportStates() {
        return List.copyOf(exportStates);
    }

    @Override
    public void clear() {
        clearCount++;
    }

    @Override
    public void display() {
        displayed = true;
    }

    public String rawText() {
        return rawText;
    }

    public String report() {
        return report;
    }

    public List<String> statuses() {
        return List.copyOf(statuses);
    }

    public List<Boolean> busyStates() {
        return List.copyOf(busyStates);
    }

    public int clearCount() {
        return clearCount;
    }

    public boolean displayed() {
        return displayed;
    }
}
