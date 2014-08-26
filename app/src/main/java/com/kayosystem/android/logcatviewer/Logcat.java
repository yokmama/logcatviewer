package com.kayosystem.android.logcatviewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class Logcat {
    private Thread mThread;
    private boolean mThreadBreak;
    private PrintLogcat mOutput;

    public static interface PrintLogcat {
        /***
         * メソッドの中でLogcatへ文字列を出力すると無限ループにはいりますので注意すること
         * 
         * @param s
         */
        public void print(String s);
    }

    public Logcat() {
    }

    public void stop() {
        mThreadBreak = true;
        mOutput = null;
    }

    public void start(PrintLogcat out) {
        mOutput = out;
        // ログデータ蓄積用のバッファを生成
        if (mThread == null) {
            // Logcat実行用のスレッドの生成と実行
            mThread = new Thread(new Runnable() {
                public void run() {
                    Process proc = null;
                    BufferedReader reader = null;
                    mThreadBreak = false;
                    try {
                        // Logcatの実行
                        ArrayList<String> commandLine = new ArrayList<String>();
                        // コマンドの作成
                        commandLine.add( "logcat");
                        commandLine.add( "-v");
                        commandLine.add( "time");
                        //commandLine.add( "-s");
                        //commandLine.add( "tag:W");
                        proc = Runtime.getRuntime().exec( commandLine.toArray( new String[commandLine.size()]));
                        reader = new BufferedReader(new InputStreamReader(proc.getInputStream()), 1024);
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (mThreadBreak) {
                                break;
                            }
                            if (mOutput != null) {
                                mOutput.print(line);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (reader != null) {
                            try {
                                // BufferedReaderを閉じる
                                reader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        // スレッドの参照を無効に設定
                        mThread = null;
                    }
                }
            });
            mThread.start();
        }
    }
}
