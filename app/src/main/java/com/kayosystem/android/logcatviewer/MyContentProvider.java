package com.kayosystem.android.logcatviewer;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import java.util.ArrayList;

public class MyContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.kayosystem.android.logcatviewer.logcat";
    public static final String CONTENT_AUTHORITY = "content://" + AUTHORITY + "/";
    public static final Uri LOGCAT_CONTENT_URI = Uri.parse(CONTENT_AUTHORITY + MyDatabaseHelper.TABLENAME);

    private static final int CODE_LOGCAT = 0;
    private static final int CODE_LOGCAT_ID = 1;

    private MyDatabaseHelper mDatabaseHelper = null;

    private UriMatcher mUriMatcher = null;

    @Override
    public boolean onCreate() {
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(AUTHORITY, MyDatabaseHelper.TABLENAME,
                CODE_LOGCAT);
        mUriMatcher.addURI(AUTHORITY, MyDatabaseHelper.TABLENAME + "/#",
                CODE_LOGCAT_ID);

        return true;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        int match = mUriMatcher.match(uri);
        switch (match) {
            case CODE_LOGCAT:
                queryBuilder.setTables(MyDatabaseHelper.TABLENAME);
                break;
            case CODE_LOGCAT_ID:
                queryBuilder.setTables(MyDatabaseHelper.TABLENAME);
                queryBuilder.appendWhere(BaseColumns._ID + "="
                        + uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URI");
        }

        Cursor cursor = queryBuilder.query(
                getSQLiteOpenHelper().getReadableDatabase(), projection, selection,
                selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int match = mUriMatcher.match(uri);
        switch (match) {
            case CODE_LOGCAT: {
                return getSQLiteOpenHelper().getWritableDatabase().delete(MyDatabaseHelper.TABLENAME, selection,
                        selectionArgs);
            }
            case CODE_LOGCAT_ID: {
                long id = ContentUris.parseId(uri);
                return getSQLiteOpenHelper().getWritableDatabase().delete(MyDatabaseHelper.TABLENAME,
                        BaseColumns._ID
                                + " = ?", new String[]{Long.toString(id)});
            }
        }
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int match = mUriMatcher.match(uri);
        switch (match) {
            case CODE_LOGCAT: {
                return ContentUris.withAppendedId(LOGCAT_CONTENT_URI,
                        getSQLiteOpenHelper().getWritableDatabase().insert(MyDatabaseHelper.TABLENAME, null,
                                values));
            }
        }
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        int match = mUriMatcher.match(uri);
        switch (match) {
            case CODE_LOGCAT: {
                return getSQLiteOpenHelper().getWritableDatabase().update(MyDatabaseHelper.TABLENAME, values,
                        selection,
                        selectionArgs);
            }
            case CODE_LOGCAT_ID: {
                long id = ContentUris.parseId(uri);
                return getSQLiteOpenHelper().getWritableDatabase().update(MyDatabaseHelper.TABLENAME, values,
                        BaseColumns._ID + " = ?",
                        new String[]{Long.toString(id)});
            }
        }
        return 0;
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        SQLiteDatabase db = getSQLiteOpenHelper().getWritableDatabase();
        try{
            db.beginTransaction();
            ContentProviderResult[] contentProviderResults = super.applyBatch(operations);
            db.setTransactionSuccessful();
            return contentProviderResults;
        }finally{
            db.endTransaction();
        }
    }

    private SQLiteOpenHelper getSQLiteOpenHelper(){
        if(mDatabaseHelper == null) {
            mDatabaseHelper = new MyDatabaseHelper(getContext());
        }
        return mDatabaseHelper;
    }
}
