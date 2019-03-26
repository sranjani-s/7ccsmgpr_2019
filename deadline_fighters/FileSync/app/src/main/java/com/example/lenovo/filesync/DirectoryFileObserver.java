package com.example.lenovo.filesync;

import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;

import java.io.File;

public class DirectoryFileObserver extends FileObserver {

    String aboslutePath;
    static final String TAG ="FileObserver";

    public DirectoryFileObserver(String dir) {
        super(dir,FileObserver.CREATE);
        aboslutePath = dir;
    }

    @Override
    public void onEvent(int event, String path) {

        switch (event){
            case FileObserver.MODIFY:
                Log.d(TAG, "MODIFY:"  + aboslutePath +"/" + path);
                break;
            case FileObserver.CREATE:
                Log.d(TAG, "CREATE:"  + aboslutePath +"/" + path);
                break;
            case FileObserver.DELETE:
                Log.d(TAG, "DELETE:"  + aboslutePath +"/" + path);
                break;
        }
    }

}
