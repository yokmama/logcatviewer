package com.kayosystem.android.logcatviewer;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends FragmentActivity implements Logcat.PrintLogcat, LoaderManager.LoaderCallbacks<Cursor>, OnCheckedChangeListener, View.OnClickListener {
    private Logcat mLogcat;
    private static final Pattern TIME_LINE = Pattern.compile(
            "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  /* timestamp [1] */
                    "(\\w)/(.+?)\\(\\s*(\\d+)\\): (.*)$");  /* level, tag, pid, msg [2-5] */

    private ListView mListview;
    private MyAdapter mAdapter;
    private EditText mEditText;
    private Handler mHandler = new Handler();
    private ToggleButton mLogcatScrollLock;
    private PackageInfo mPackInfo;
    private String mSendTo;

    SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmssSSS");
    SimpleDateFormat format2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.main);

        mListview = (ListView) findViewById(android.R.id.list);
        mEditText = (EditText) findViewById(R.id.logcat_sender_editText);
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                reloadLogcat();
            }
        });

        mLogcatScrollLock = (ToggleButton) findViewById(R.id.logcat_sender_logcatScroll);
        mLogcatScrollLock.setOnCheckedChangeListener(this);

        findViewById(R.id.logcat_sender_buttonSave).setOnClickListener(this);
        findViewById(R.id.logcat_sender_buttonShare).setOnClickListener(this);

        mAdapter = new MyAdapter(this, null);
        mListview.setAdapter(mAdapter);
        try {
            mPackInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLogcat();
        reloadLogcat();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        getSupportLoaderManager().destroyLoader(R.layout.main);
        stopLogcat();
        super.onDestroy();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.logcat_sender_logcatScroll) {
            reloadLogcat();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.logcat_sender_buttonSave) {
            FolderSelectDialog dlg = FolderSelectDialog.newInstance();
            dlg.setFolderSelectDialogCallback(new FolderSelectDialog.FolderSelectDialogCallback() {
                @Override
                public void onResult(File folder) {
                    Calendar cal = Calendar.getInstance();
                    String name = "logcat-" + format.format(cal.getTime()) + ".txt";

                    File saveFile = new File(folder, name);
                    if (writeFile(saveFile)) {
                        Toast.makeText(MainActivity.this, R.string.logcat_sender_save_success, Toast.LENGTH_SHORT).show();
                    }
                }
            });
            dlg.show(getSupportFragmentManager(), "dialog");

        } else if (v.getId() == R.id.logcat_sender_buttonShare) {
            File savefile = getCacheFile();
            if (writeFile(savefile)) {
                sendMail(savefile);
            }
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String where = null;
        String[] whereArgs = null;

        String filter = mEditText.getText().toString();
        if (filter.length() > 0) {
            where = MyDatabaseHelper.FIELDLOG + " like '%' || ? || '%' ESCAPE '$'";
            whereArgs = new String[]{filter};
        }
        return new CursorLoader(this,
                MyContentProvider.LOGCAT_CONTENT_URI, null, where, whereArgs, BaseColumns._ID);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mAdapter.changeCursor(cursor);
        mAdapter.notifyDataSetChanged();
        if (mLogcatScrollLock.isChecked()) {
            mListview.setSelection(mAdapter.getCount());
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    @Override
    public void print(String line) {
        Matcher tm = TIME_LINE.matcher(line);
        if (tm.matches()) {
            String level = tm.group(2);
            ContentValues values = new ContentValues();
            values.put(MyDatabaseHelper.FIELDLEVEL, level);
            values.put(MyDatabaseHelper.FIELDLOG, line);
            getContentResolver().insert(MyContentProvider.LOGCAT_CONTENT_URI, values);
        } else {
            ContentValues values = new ContentValues();
            values.put(MyDatabaseHelper.FIELDLEVEL, "");
            values.put(MyDatabaseHelper.FIELDLOG, line);
            getContentResolver().insert(MyContentProvider.LOGCAT_CONTENT_URI, values);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                reloadLogcat();
            }
        });
    }

    private File getCacheFile() {
        File attach = new File(Environment.getExternalStorageDirectory(), "attach");
        if (!attach.exists()) {
            attach.mkdirs();
        }
        File tempFile = new File(attach, "logcat.txt");
        if (tempFile.exists()) {
            tempFile.delete();
        }
        return tempFile;
    }

    private void reloadLogcat() {
        getSupportLoaderManager().restartLoader(R.layout.main, null, this);
    }

    private void startLogcat() {
        if (mLogcat == null) {
            getContentResolver().delete(MyContentProvider.LOGCAT_CONTENT_URI, null, null);
            mLogcat = new Logcat();
            mLogcat.start(this);
        }
    }

    private void stopLogcat() {
        if (mLogcat != null) {
            mLogcat.stop();
            mLogcat = null;
        }
    }

    private boolean writeFile(File savefile) {
        try {
            FileOutputStream writer = new FileOutputStream(savefile);

            String where = null;
            String[] whereArgs = null;

            String filter = mEditText.getText().toString();
            if (filter.length() > 0) {
                where = MyDatabaseHelper.FIELDLOG + " like '%' || ? || '%' ESCAPE '$'";
                whereArgs = new String[]{filter};
            }

            Cursor cursor = getContentResolver().query(MyContentProvider.LOGCAT_CONTENT_URI, new String[]{
                    MyDatabaseHelper.FIELDLOG
            }, where, whereArgs, BaseColumns._ID);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String line = cursor.getString(0);
                        writer.write(line.getBytes("ISO-2022-JP"));
                        writer.write('\n');
                    } while (cursor.moveToNext());
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            writer.close();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        }
        return false;
    }

    private boolean sendMail(File savefile) {
        try {
            Date now = Calendar.getInstance().getTime();
            String subject = "Logcat dump file";
            StringBuilder body = new StringBuilder();
            body.append("pname:").append(mPackInfo.packageName).append("\n");
            body.append("dev:").append(Build.DEVICE).append("\n");
            body.append("mod:").append(Build.MODEL).append("\n");
            body.append("sdk:").append(Build.VERSION.CODENAME).append("\n");
            body.append("ver:").append(mPackInfo.versionName).append("\n");
            body.append("date:").append(format2.format(now)).append("\n");

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            if (mSendTo != null) {
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{mSendTo});
            } else {
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{});
            }
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT, body.toString());
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(savefile));
            intent.setType("text/plain");
            startActivity(intent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    class MyAdapter extends CursorAdapter {
        LayoutInflater mLayoutInflater;
        int colorv;
        int colord;
        int colori;
        int colorw;
        int colore;
        int coloro;

        public MyAdapter(Context context, Cursor c) {
            super(context, c, false);
            mLayoutInflater = LayoutInflater.from(context);
            colorv = context.getResources().getColor(R.color.logcat_sender_color_v);
            colord = context.getResources().getColor(R.color.logcat_sender_color_d);
            colori = context.getResources().getColor(R.color.logcat_sender_color_i);
            colorw = context.getResources().getColor(R.color.logcat_sender_color_w);
            colore = context.getResources().getColor(R.color.logcat_sender_color_e);
            coloro = context.getResources().getColor(R.color.logcat_sender_color_o);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mLayoutInflater.inflate(R.layout.rowdata, parent, false);
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView textView = (TextView) view;

            String level = cursor.getString(cursor.getColumnIndex(MyDatabaseHelper.FIELDLEVEL));
            String line = cursor.getString(cursor.getColumnIndex(MyDatabaseHelper.FIELDLOG));

            textView.setText(line);
            if("V".equals(level)){
                textView.setTextColor(colorv);
            }else if("D".equals(level)){
                textView.setTextColor(colord);
            }else if("I".equals(level)){
                textView.setTextColor(colori);
            }else if("W".equals(level)){
                textView.setTextColor(colorw);
            }else if("E".equals(level)){
                textView.setTextColor(colore);
            }else{
                textView.setTextColor(coloro);
            }

        }
    }

}