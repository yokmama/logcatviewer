package com.kayosystem.android.logcatviewer;

/**
* Created by kayosystem on 2014/08/04.
*/
public class FileItem {

    public static final int ITEMTYPE_PARENT = 0;
    public static final int ITEMTYPE_DRIVE = 1;
    public static final int ITEMTYPE_FOLDER = 2;
    public String mName;

    public String mIconUrl;

    public int mIconId;

    public boolean isSelected;

    public Object mElement;

    public int mType;

    public FileItem(String name, int type, Object element) {
        mName = name;
        isSelected = false;
        mElement = element;
        mType = type;
    }
}
