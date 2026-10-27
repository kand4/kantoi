package com.kantoi.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * Pengurus Navigasi Bawah Terapung KANTOI (Floating Persistent Bottom Bar)
 * Menyediakan dok navigasi 5-item yang kekal merentasi semua paparan utama:
 * [📞 Panggilan] [📖 Kenalan] [🔢 Pad Dail] [💬 Mesej] [🛡️ Pertahanan]
 */
public class KantoiBottomBar {
    public static final int TAB_CALLS = 0;
    public static final int TAB_CONTACTS = 1;
    public static final int TAB_KEYPAD = 2;
    public static final int TAB_SMS = 3;
    public static final int TAB_SHIELD = 4;

    public static View buildBottomNavBar(final Activity activity, final int activeTab) {
        final Context context = activity;
        LinearLayout navBar = new LinearLayout(context);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setBackgroundColor(0xFF090D16); // Deep Obsidian Stealth Dark
        navBar.setPadding(dpToPx(context, 8), dpToPx(context, 6), dpToPx(context, 8), dpToPx(context, 8));
        navBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 62)));

        // 1. Calls Tab (Panggilan)
        Button btnCalls = createNavButton(context, "📞", "Panggilan", activeTab == TAB_CALLS);
        btnCalls.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).selectTab(0);
                } else {
                    Intent it = new Intent(activity, MainActivity.class);
                    it.putExtra("select_tab", 0);
                    it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    activity.startActivity(it);
                    activity.overridePendingTransition(0, 0);
                }
            }
        });
        navBar.addView(btnCalls);

        // 2. Contacts Tab (Kenalan)
        Button btnContacts = createNavButton(context, "📖", "Kenalan", activeTab == TAB_CONTACTS);
        btnContacts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity instanceof ContactsActivity) return;
                Intent it = new Intent(activity, ContactsActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                activity.startActivity(it);
                activity.overridePendingTransition(0, 0);
            }
        });
        navBar.addView(btnContacts);

        // 3. Keypad Tab (Pad Dail)
        Button btnKeypad = createNavButton(context, "🔢", "Pad Dail", activeTab == TAB_KEYPAD);
        btnKeypad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity instanceof DialerActivity) return;
                Intent it = new Intent(activity, DialerActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                activity.startActivity(it);
                activity.overridePendingTransition(0, 0);
            }
        });
        navBar.addView(btnKeypad);

        // 4. SMS Tab (Mesej)
        Button btnSms = createNavButton(context, "💬", "Mesej", activeTab == TAB_SMS);
        btnSms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity instanceof SmsActivity) return;
                Intent it = new Intent(activity, SmsActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                activity.startActivity(it);
                activity.overridePendingTransition(0, 0);
            }
        });
        navBar.addView(btnSms);

        // 5. Shield Tab (Pertahanan)
        Button btnShield = createNavButton(context, "🛡️", "Pertahanan", activeTab == TAB_SHIELD);
        btnShield.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).selectTab(1);
                } else {
                    Intent it = new Intent(activity, MainActivity.class);
                    it.putExtra("select_tab", 1);
                    it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    activity.startActivity(it);
                    activity.overridePendingTransition(0, 0);
                }
            }
        });
        navBar.addView(btnShield);

        return navBar;
    }

    private static Button createNavButton(Context context, String icon, String label, boolean selected) {
        Button btn = new Button(context);
        btn.setText(icon + "\n" + label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(context, 50), 1.0f);
        btn.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        if (selected) {
            bg.setColor(0xFF131B2E); // Deep Obsidian Card Surface
            bg.setCornerRadius(dpToPx(context, 10));
            bg.setStroke(dpToPx(context, 1), 0xFF0284C7); // Electric Cyber Blue Stroke
            btn.setBackground(bg);
            btn.setTextColor(0xFF22D3EE); // Cyber Cyan Highlight
        } else {
            btn.setBackground(null);
            btn.setTextColor(0xFF64748B); // Muted Slate
        }
        return btn;
    }

    public static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
