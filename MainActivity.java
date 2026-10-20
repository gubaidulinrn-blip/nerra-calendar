package com.nerra.calendar;

import android.Manifest;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int)(24 * getResources().getDisplayMetrics().density);
        box.setPadding(p,p,p,p); box.setBackgroundColor(Color.rgb(39,42,45));
        ScrollView scroll = new ScrollView(this); scroll.addView(box); setContentView(scroll);
        TextView heading = new TextView(this); heading.setText("NERRA\nCalendar Widget"); heading.setTextSize(25); heading.setTextColor(0xffb79a62); box.addView(heading);
        TextView info = new TextView(this);
        info.setText("\nNERRA Classic · прозрачный фон\n\nСначала события на весь день, затем события по времени. Все события — в одном прокручиваемом списке. Маркер использует цвет события или календаря проекта.\n\nПодключите Яндекс Календарь через DAVx5 и включите синхронизацию календарей Android. Виджет читает видимые системные календари; пароль Яндекса ему не нужен.\n\nАвтоцвет следует светлой/тёмной теме телефона. Для конкретных обоев выберите цвет вручную. Палитра графит/золото — предварительная, не подтверждённые бренд-цвета.\n");
        info.setTextColor(0xfff1eee7); info.setTextSize(15); box.addView(info);
        button(box,"Разрешить доступ к календарю", () -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},1));
        String[] labels = {"Автоцвет по теме телефона", "Светлый текст для тёмных обоев", "Графитовый текст для светлых обоев"};
        RadioGroup group = new RadioGroup(this);
        int mode = getSharedPreferences("settings",0).getInt("mode",0);
        for(int i=0;i<labels.length;i++) { RadioButton b = new RadioButton(this); b.setId(100+i); b.setText(labels[i]); b.setTextColor(0xfff1eee7); group.addView(b); }
        group.check(100+mode); box.addView(group);
        group.setOnCheckedChangeListener((g,id)-> { getSharedPreferences("settings",0).edit().putInt("mode",id-100).apply(); refresh(); });
        button(box,"Добавить виджет",()-> { AppWidgetManager m=AppWidgetManager.getInstance(this); if(m.isRequestPinAppWidgetSupported()) m.requestPinAppWidget(new ComponentName(this,CalendarWidget.class),null,null); else Toast.makeText(this,"Удерживайте рабочий стол → Виджеты → NERRA Calendar",Toast.LENGTH_LONG).show(); });
        button(box,"Обновить события",this::refresh);
    }
    private void button(LinearLayout box,String label,Runnable action) { Button b=new Button(this); b.setText(label); b.setOnClickListener(v->action.run()); box.addView(b); }
    private void refresh() { sendBroadcast(new Intent(this,CalendarWidget.class).setAction("com.nerra.calendar.REFRESH")); }
    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) { super.onRequestPermissionsResult(request,permissions,results); refresh(); Toast.makeText(this,checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED?"Доступ разрешён":"Без доступа события не отображаются",Toast.LENGTH_LONG).show(); }
}
