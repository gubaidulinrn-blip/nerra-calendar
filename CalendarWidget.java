package com.nerra.calendar;

import android.Manifest;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

public class CalendarWidget extends AppWidgetProvider {
    @Override public void onReceive(Context context,Intent intent) {
        if("com.nerra.calendar.OPEN_EVENT".equals(intent.getAction())) {
            Uri data=intent.getData();
            if(data!=null && "com.android.calendar".equals(data.getAuthority())) {
                Intent openEvent=new Intent(Intent.ACTION_VIEW,data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                openEvent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,0));
                openEvent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME,0));
                try { context.startActivity(openEvent); } catch(ActivityNotFoundException ignored) { context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            }
            return;
        }
        super.onReceive(context,intent);
        String a=intent.getAction();
        if(!AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a) && !AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(a) && !AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(a) && !AppWidgetManager.ACTION_APPWIDGET_ENABLED.equals(a) && !AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(a)) launch(context);
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) { launch(c); }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options) { launch(c); }
    private void launch(Context context) {
        PendingResult pending=goAsync(); Context c=context.getApplicationContext();
        java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
        worker.execute(()-> { try { update(c); } finally { pending.finish(); worker.shutdown(); } });
    }
    static class Event { long id,begin,end; boolean all; String title; int color; }
    private void update(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        int[] ids=manager.getAppWidgetIds(new ComponentName(c,CalendarWidget.class));
        if(ids.length==0)return;
        LocalDate today=LocalDate.now(); ZoneId zone=ZoneId.systemDefault();
        long start=today.atStartOfDay(zone).toInstant().toEpochMilli(), stop=today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long utcStart=today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),utcStop=today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        List<Event> events=new ArrayList<>(); String empty="Сегодня событий нет";
        if(c.checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED) empty="Откройте NERRA и разрешите доступ к календарю";
        else {
            Uri.Builder uri=CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(uri,Math.min(start,utcStart)); ContentUris.appendId(uri,Math.max(stop,utcStop));
            String[] cols={"event_id","begin","end","allDay","title","displayColor"};
            try(Cursor cur=c.getContentResolver().query(uri.build(),cols,"visible=1 AND status!=2",null,null)) {
                if(cur!=null) while(cur.moveToNext()) {
                    Event e=new Event(); e.id=cur.getLong(0); e.begin=cur.getLong(1); e.end=cur.getLong(2); e.all=cur.getInt(3)!=0;
                    long lo=e.all?utcStart:start,hi=e.all?utcStop:stop;
                    if(e.begin>=hi || (e.end<=lo && !(e.begin==e.end && e.begin>=lo)))continue;
                    e.title=cur.getString(4); if(e.title==null||e.title.trim().isEmpty())e.title="Без названия";
                    e.color=cur.getInt(5); if(e.color==0)e.color=0xffb79a62; else e.color|=0xff000000;
                    events.add(e);
                }
            } catch(RuntimeException error) { empty="Не удалось прочитать календарь. Откройте NERRA и проверьте доступ."; events.clear(); }
        }
        events.sort(Comparator.comparing((Event e)->!e.all).thenComparingLong(e->e.begin).thenComparing(e->e.title));
        int mode=c.getSharedPreferences("settings",0).getInt("mode",0);
        boolean dark=(c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        int text=(mode==1 || mode==0 && dark)?0xfff1eee7:0xff272a2d;
        PendingIntent settings=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent refresh=PendingIntent.getBroadcast(c,1,new Intent(c,CalendarWidget.class).setAction("com.nerra.calendar.REFRESH"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(c,CalendarWidget.class).setAction("com.nerra.calendar.OPEN_EVENT");
        PendingIntent eventTemplate=PendingIntent.getBroadcast(c,2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        for(int widgetId:ids) {
            RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.widget);
            view.setTextViewText(R.id.date,today.format(DateTimeFormatter.ofPattern("d MMMM · EEE",new Locale("ru"))));
            view.setTextColor(R.id.date,text); view.setTextColor(R.id.empty,text);
            view.setTextViewText(R.id.empty,empty); view.setEmptyView(R.id.events,R.id.empty);
            view.setOnClickPendingIntent(R.id.brand,settings); view.setOnClickPendingIntent(R.id.empty,settings); view.setOnClickPendingIntent(R.id.refresh,refresh);
            Intent day=new Intent(Intent.ACTION_VIEW,Uri.parse("content://com.android.calendar/time/"+start));
            view.setOnClickPendingIntent(R.id.date,PendingIntent.getActivity(c,3,day,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            RemoteViews.RemoteCollectionItems.Builder items=new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(false).setViewTypeCount(1);
            long index=0;
            for(Event e:events) {
                RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.event);
                row.setTextViewText(R.id.title,e.title); row.setTextColor(R.id.title,text); row.setTextColor(R.id.time,text); row.setTextColor(R.id.marker,e.color);
                String time=e.all?"Весь день":android.text.format.DateFormat.getTimeFormat(c).format(new Date(e.begin));
                if(!e.all && e.begin<start)time="↤ "+time;
                row.setTextViewText(R.id.time,time);
                Intent fill=new Intent().setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,e.id)).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,e.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,e.end);
                row.setOnClickFillInIntent(R.id.event,fill); items.addItem(index++,row);
            }
            view.setPendingIntentTemplate(R.id.events,eventTemplate); view.setRemoteAdapter(R.id.events,items.build()); manager.updateAppWidget(widgetId,view);
        }
    }
}
