package com.kayosystem.android.logcatviewer;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Environment;
import android.provider.BaseColumns;
import android.telephony.TelephonyManager;
import android.util.Log;

public class MyDatabaseHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;

    public static final String TABLENAME = "logcat";
    public static final String FIELDLEVEL = "level";
    public static final String FIELDLOG = "log";


    public MyDatabaseHelper(Context context) {
        super(context, null, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // データベース処理開始
        db.beginTransaction();
        try {
            // テーブル作成を実行
            db.execSQL("CREATE TABLE " + TABLENAME + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + FIELDLEVEL + " INTEGER,"
                    + FIELDLOG + " TEXT"
                    + ");");

            // SQL処理を反映
            db.setTransactionSuccessful();
        } finally {
            //データベース処理終了
            db.endTransaction();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // テーブルが存在する場合は削除する
        db.execSQL("DROP TABLE IF EXISTS " + TABLENAME);
        // テーブルを生成する
        onCreate(db);
    }
}