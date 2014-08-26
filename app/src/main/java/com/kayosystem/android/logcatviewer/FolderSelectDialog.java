package com.kayosystem.android.logcatviewer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by kayosystem on 2014/08/04.
 */
public class FolderSelectDialog extends DialogFragment implements AdapterView.OnItemClickListener {
    private final String PATH_SDCARD = Environment.getExternalStorageDirectory()
            .getAbsolutePath();

    private List<String> mBreadcrumbs = new ArrayList<String>();
    private ListView mListView;
    private FolderSelectDialogCallback mFolderSelectDialogCallback;

    public interface FolderSelectDialogCallback {
        void onResult(File folder);
    }

    public static FolderSelectDialog newInstance() {
        FolderSelectDialog instance = new FolderSelectDialog();
        return instance;
    }

    @Override
    public Dialog onCreateDialog(Bundle b) {
        // ダイアログのコンテンツ部分
        LayoutInflater i
                = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View content = i.inflate(R.layout.dialog_folderselect, null);

        mListView = (ListView) content.findViewById(android.R.id.list);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // タイトル
        builder.setTitle("Select Folder");
        // コンテンツ
        builder.setView(content);
        // Select
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File folder = getSelectFolder();
                if (mFolderSelectDialogCallback != null && folder != null) {
                    mFolderSelectDialogCallback.onResult(folder);
                }
            }
        });
        // Cancel
        builder.setNegativeButton(android.R.string.cancel, null);

        Dialog dialog = builder.create();

        // ダイアログ外タップで消えないように設定
        dialog.setCanceledOnTouchOutside(false);

        mListView.setOnItemClickListener(this);
        buildList();


        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        FileItem item = (FileItem) mListView.getItemAtPosition(position);

        if (item.mType == FileItem.ITEMTYPE_FOLDER || item.mType == FileItem.ITEMTYPE_DRIVE) {
            //Directory
            mBreadcrumbs.add(makeFileId(item.mElement));
            buildList();
        } else if (item.mType == FileItem.ITEMTYPE_PARENT) {
            if (mBreadcrumbs.size() > 0) {
                mBreadcrumbs.remove(mBreadcrumbs.size() - 1);
            }
            buildList();
        }
    }

    public void setFolderSelectDialogCallback(FolderSelectDialogCallback callback) {
        mFolderSelectDialogCallback = callback;
    }

    public final List<FileItem> getSelectedFile() {
        List<FileItem> list = new ArrayList<FileItem>();
        for (int i = 0; i < mListView.getCount(); i++) {
            FileItem item = (FileItem) mListView.getItemAtPosition(i);
            if (item.isSelected) {
                list.add(item);
            }
        }
        return list;
    }

    private File getSelectFolder() {
        List<FileItem> items = getSelectedFile();
        if (items.size() > 0) {
            FileItem selected = items.get(0);
            return new File((String) selected.mElement);

        } else {
            if (mBreadcrumbs.size() > 0) {
                String parent = mBreadcrumbs.get(mBreadcrumbs.size() - 1);
                File folder = new File(parent);
                if (folder.canWrite()) {
                    return folder;
                }
            }
        }
        return null;
    }

    private void buildList() {
        BuildListTask task = new BuildListTask(getActivity());
        task.execute();
    }

    private String makeFileId(Object element) {
        String file = (String) element;
        return file;
    }

    public List<FileItem> OnBuildListTask(List<FileItem> list, String parentId) {
        String target = null;

        if (parentId == null) {
            //Rootフォルダの場合はSDCardフォルダのショートカットを追加
            boolean mounted = Environment.getExternalStorageState()
                    .equals(Environment.MEDIA_MOUNTED);

            if (mounted) {
                FileItem item = new FileItem(PATH_SDCARD + getString(R.string.logcat_sender_file_dialog_item_sdcard), FileItem.ITEMTYPE_DRIVE, PATH_SDCARD);
                item.mIconId = R.drawable.ic_action_sdcard;
                list.add(item);
            }
            target = "/";
        } else {
            //サブフォルダの場合は、上へ移動するためのショートカットを追加
            //childs
            FileItem item = new FileItem(getString(R.string.logcat_sender_file_dialog_item_parent),
                    FileItem.ITEMTYPE_PARENT, null);
            item.mIconId = R.drawable.ic_action_back;
            list.add(item);
            target = parentId;
        }

        File f = new File(target);
        File[] files = f.listFiles();
        if(files!=null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    FileItem item = new FileItem(files[i].getName(), FileItem.ITEMTYPE_FOLDER, files[i].getAbsolutePath());
                    item.mIconId = R.drawable.ic_action_folder;
                    list.add(item);
                }
            }
        }

        return list;
    }

    public class BuildListTask extends AsyncTask<Void, Integer, List<FileItem>> {

        private Context mContext;

        private ProgressDialog mProgressDialog;

        public BuildListTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setMessage("Searching...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected List<FileItem> doInBackground(Void... voids) {

            List<FileItem> result = new ArrayList<FileItem>();
            String parent = null;

            if (mBreadcrumbs.size() > 0) {
                parent = mBreadcrumbs.get(mBreadcrumbs.size() - 1);
            }
            final String title = parent == null ? "/" : parent;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //getActionBar().setTitle(title);
                    //Folder名をどっかに設定？
                }
            });

            //proc listing file list
            List<FileItem> list = OnBuildListTask(result, parent);
            Collections.sort(list, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem fileItem,
                                   FileItem fileItem2) {
                    String str1 = fileItem.mType + fileItem.mName;
                    String str2 = fileItem2.mType + fileItem2.mName;
                    return str1.compareTo(str2);
                }
            });

            return list;
        }

        @Override
        protected void onPostExecute(List<FileItem> fileItems) {
            mProgressDialog.dismiss();

            FileItemAdapter adapter = new FileItemAdapter(mContext, fileItems);
            mListView.setAdapter(adapter);
        }
    }

    public class FileItemAdapter extends ArrayAdapter<FileItem> implements View.OnClickListener {

        private LayoutInflater mLayoutInflater;

        public FileItemAdapter(Context context, List<FileItem> list) {
            super(context, -1, list);
            mLayoutInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView != null) {
                holder = (ViewHolder) convertView.getTag();
            } else {
                convertView = mLayoutInflater.inflate(R.layout.folderselect_list_item_row, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
                holder.checkBox.setOnClickListener(this);
            }

            FileItem item = getItem(position);

            holder.textName.setText(item.mName);
            holder.checkBox.setChecked(item.isSelected);
            holder.checkBox.setTag(item);
            if ((item.mType == FileItem.ITEMTYPE_FOLDER && new File((String) item.mElement).canWrite())) {
                holder.checkBox.setVisibility(View.VISIBLE);
            } else {
                holder.checkBox.setVisibility(View.INVISIBLE);
            }

            holder.imageIcon.setImageResource(item.mIconId);

            return convertView;
        }

        @Override
        public void onClick(View view) {
            FileItem tagItem = (FileItem) view.getTag();
            for (int i = 0; i < getCount(); i++) {
                FileItem item = getItem(i);
                if (item == tagItem) {
                    item.isSelected = !item.isSelected;
                } else {
                    item.isSelected = false;
                }
            }
            // アダプタ内容を即時反映する
            notifyDataSetChanged();
        }
    }

    protected class ViewHolder {
        ImageView imageIcon;
        TextView textName;
        CheckBox checkBox;

        public ViewHolder(View view) {
            imageIcon = (ImageView) view.findViewById(R.id.logcat_sender_imageIcon);
            textName = (TextView) view.findViewById(R.id.logcat_sender_textName);
            checkBox = (CheckBox) view.findViewById(R.id.logcat_sender_checkBox);
        }
    }
}
