package com.example.lenovo.filesync;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.database.Cursor;
import android.os.FileObserver;
import android.provider.DocumentsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.mobileconnectors.s3.transferutility.*;
import com.amazonaws.mobile.client.AWSStartupHandler;
import com.amazonaws.mobile.client.AWSStartupResult;

import com.amazonaws.auth.BasicAWSCredentials;

import com.amazonaws.services.s3.model.*;

import com.amazonaws.util.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import android.database.sqlite.*;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final String KEY = "xxx";
    private final String SECRET = "xxx";
    private final String bucketName = "xxx";
    private final String filepath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "FileSync";

    private AmazonS3Client s3Client;
    private BasicAWSCredentials credentials;

    private static final int CHOOSING_FILE_REQUEST = 1234;

    private TextView tvFileName;
    private EditText edtFileName;
    private ListView lvItemList;
    private TextView tvFileInfo;

    private Uri fileUri;
    private DirectoryFileObserver directoryFileObserver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        edtFileName = findViewById(R.id.edt_file_name);
        tvFileName = findViewById(R.id.tv_file_name);
        tvFileName.setText("");

        lvItemList = findViewById(R.id.lv_item_list);
        tvFileInfo = findViewById(R.id.tv_file_info);
        tvFileInfo.setText("No file selected.");

        findViewById(R.id.btn_choose_file).setOnClickListener(this);
        findViewById(R.id.btn_upload).setOnClickListener(this);
        findViewById(R.id.btn_download).setOnClickListener(this);
        findViewById(R.id.btn_server_files).setOnClickListener(this);
        findViewById(R.id.btn_local_file).setOnClickListener(this);

        AWSMobileClient.getInstance().initialize(this, new AWSStartupHandler() {
            @Override
            public void onComplete(AWSStartupResult awsStartupResult) {
                Log.d("MainActivity", "AWSMobileClient is instantiated and you are connected to AWS!");
            }
        }).execute();

        credentials = new BasicAWSCredentials(KEY, SECRET);
        s3Client = new AmazonS3Client(credentials);

        directoryFileObserver = new DirectoryFileObserver(filepath);
        directoryFileObserver.startWatching();

    }

    public void openDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        final AlertDialog dialog = builder.create();
        View dialogView = View.inflate(getApplicationContext(), R.layout.dialog_item_function, null);

        dialog.setView(dialogView);
        dialog.show();

        TextView tvFileSelect = dialogView.findViewById(R.id.tv_file_select);
        Button btnUploadFile = dialogView.findViewById(R.id.btn_upload_file);
        Button btnDownloadFile = dialogView.findViewById(R.id.btn_download_file);
        Button btnDeleteFile = dialogView.findViewById(R.id.btn_delete_file);
        Button btnRenameFile = dialogView.findViewById(R.id.btn_rename_file);

        tvFileSelect.setText(tvFileInfo.getText().toString());

        btnUploadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { uploadFile(); }
        });

        btnDownloadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {downloadFile(); }
        });

        btnDeleteFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { deleteFile(); }
        });
        btnRenameFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { renameFile(tvFileInfo.getText().toString(),edtFileName.getText().toString()); }
        });
    }

    private void uploadFile() {
        final String fileNameTv = tvFileInfo.getText().toString();
        TransferObserver uploadObserver;
        File file;
        if(fileNameTv != null){

            if (fileUri != null) {

                final String fileNameEdt = edtFileName.getText().toString();

                if (!validateInputFileName(fileNameEdt)) {
                    return;
                }

                file = new File(filepath, "/" + fileNameEdt+ "." + getFileExtension(fileUri));

                createFile(getApplicationContext(), fileUri, file);

                TransferUtility transferUtility =
                        TransferUtility.builder()
                                .context(getApplicationContext())
                                .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                                .s3Client(s3Client)
                                .build();

                uploadObserver = transferUtility.upload(fileNameEdt + "." + getFileExtension(fileUri), file);

                fileUri = null;
            }else{
                file = new File(filepath, "/" + fileNameTv);

                TransferUtility transferUtility =
                        TransferUtility.builder()
                                .context(getApplicationContext())
                                .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                                .s3Client(s3Client)
                                .build();

                uploadObserver = transferUtility.upload(fileNameTv, file);


            }
            uploadObserver.setTransferListener(new TransferListener() {

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        Toast.makeText(getApplicationContext(), "Upload Completed!", Toast.LENGTH_SHORT).show();


                    } else if (TransferState.FAILED == state) {
                        Toast.makeText(getApplicationContext(), "Upload Failed!", Toast.LENGTH_SHORT).show();
                        file.delete();
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int) percentDonef;

                    tvFileName.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    ex.printStackTrace();
                }

            });
        }

    }

    private void uploadFiles(){
        List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String,Object>>();
        fileList = getLocalFiles(filepath);

        for(int i=0; i<fileList.size(); ++i){
            String fileName = fileList.get(i).get("fileName").toString();
            tvFileInfo.setText(fileName);
            uploadFile();
        }
    }

    public void renameFile(final String fileNameTv, final String fileNameEdt){

        if (!validateInputFileName(fileNameEdt)){
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String ex = getFileExtension(fileNameTv);
                String fileName = getFileNameNoEx(fileNameTv);
                if (fileName != fileNameEdt){
                    try {
                        s3Client.copyObject(bucketName, fileNameTv, bucketName, fileNameEdt + "." + ex);
                        s3Client.deleteObject(bucketName,fileNameTv);
                        renameLocalFile(fileNameTv,fileNameEdt);
                    } catch (AmazonServiceException e) {
                        System.err.println(e.getErrorMessage());
                        System.exit(1);
                    }
                }
            }
        }).start();

    }

    private void renameLocalFile(final String fileNameTv, final String fileNameEdt){

        final File file = new File(filepath, "/" + fileNameEdt+ "." + getFileExtension(fileUri));

        createFile(getApplicationContext(), fileUri, file);
        deleteLocalFile();
    }

    private void listServerFile(){
        List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String,Object>>();
        fileList = getFile();

        SimpleAdapter simpleAdapter = new SimpleAdapter(
                this,
                fileList,
                R.layout.spec_item_list,
                new String[]{"seq","filename"},
                new int[]{R.id.tv_item_seq, R.id.tv_item_name}
        );
        lvItemList.setAdapter(simpleAdapter);
        setListViewHeightBasedOnChildren(lvItemList);


        final List<HashMap<String, Object>> finalFileList = fileList;
        lvItemList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final String fileName = finalFileList.get(i).get("filename").toString();
                System.out.println(finalFileList.get(i));
                tvFileInfo.setText(fileName);
                openDialog();
            }
        });
    }

    private void listLocalFile(){
        List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String,Object>>();
        fileList = getLocalFiles(filepath);

        SimpleAdapter simpleAdapter = new SimpleAdapter(
                this,
                fileList,
                R.layout.spec_item_list,
                new String[]{"seq","fileName"},
                new int[]{R.id.tv_item_seq, R.id.tv_item_name}
        );
        lvItemList.setAdapter(simpleAdapter);
        setListViewHeightBasedOnChildren(lvItemList);


        final List<HashMap<String, Object>> finalFileList = fileList;
        lvItemList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final String fileName = finalFileList.get(i).get("fileName").toString();
                System.out.println(finalFileList.get(i));
                tvFileInfo.setText(fileName);
                openDialog();
            }
        });
    }

    /**
     * Taken from Stack Overflow - https://stackoverflow.com/a/26501296
     * Updates the ListView height based on its children
     *
     * @param listView the ListView to adjust
     *
     * This is only for interface and I want to focus on the functionality.
     */

    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            // pre-condition
            return;
        }

        int totalHeight = 0;
        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.AT_MOST);
        for (int i = 0; i < listAdapter.getCount(); ++i) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
            totalHeight += listItem.getMeasuredHeight();
        }

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }

    private List<HashMap<String, Object>> getFile(){

        getFileCallable getFileCallable = new getFileCallable();
        FutureTask<List<HashMap<String, Object>>> result = new FutureTask<>(getFileCallable);
        new Thread(result).start();
        List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String,Object>>();
        try {
            fileList = result.get();

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return fileList;
    }

    private void downloadFile() {
        if (isPathExist(filepath)) {

        final String fileName = tvFileInfo.getText().toString();


            //                final File localFile = File.createTempFile("tmp","."+getFileExtension(fileName));
            final File localFile = new File(filepath, "/" + fileName);

            Log.d("downloadFile", "Get file path: " + localFile.getAbsolutePath());

            TransferUtility transferUtility =
                    TransferUtility.builder()
                            .context(getApplicationContext())
                            .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                            .s3Client(s3Client)
                            .build();

            TransferObserver downloadObserver =
                    transferUtility.download(fileName,localFile);

            downloadObserver.setTransferListener(new TransferListener() {

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        Toast.makeText(getApplicationContext(), "Download:" + fileName, Toast.LENGTH_SHORT).show();

                        tvFileName.setText(fileName);
                        Log.d("MainActivity", "Get file path: " + localFile.getAbsolutePath());

                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int) percentDonef;

                    tvFileName.setText("ID:" + id + "|bytesCurrent: " + bytesCurrent + "|bytesTotal: " + bytesTotal + "|" + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    ex.printStackTrace();
                }

            });
        }else {
            Toast.makeText(this, "File path is not exist.", Toast.LENGTH_LONG).show();
        }
    }

    private void downloadFiles(){
        List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String,Object>>();
        fileList = getFile();

        for(int i=0; i<fileList.size(); ++i){
            final String fileName = fileList.get(i).get("filename").toString();
            tvFileInfo.setText(fileName);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    downloadFile();
                }
            }).start();

        }
        Toast.makeText(getApplicationContext(), "Download Completed!" , Toast.LENGTH_SHORT).show();

    }

    private void deleteFile(){


        new Thread(new Runnable() {
            @Override
            public void run() {
                final String fileName = tvFileInfo.getText().toString();
                s3Client.deleteObject(bucketName,fileName);
            }
        }).start();
        deleteLocalFile();

    }

    public List<HashMap<String, Object>> getLocalFiles(String dirPath) {
        File file = new File(dirPath);
        int seq = 0;
        if(!file.exists()){
            return null;
        }else{

            File[] files = file.listFiles();

            if(files==null){
                return null;
            }

            List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String, Object>>();
            for (File f : files) {
                if(f.isFile()){
                    String filename=f.getName();
                    Log.d("LOGCAT","fileName:"+filename);
                    java.text.SimpleDateFormat simpleDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String fileLastModified = simpleDateFormat.format(f.lastModified()) ;
                    Log.d("LOGCAT","lastModified:"+fileLastModified);

                    try {
                        ++seq;
                        HashMap<String, Object> hashMap = new HashMap<String, Object>();
                        hashMap.put("seq", seq);
                        hashMap.put("fileName", filename);
                        hashMap.put("LastModified", fileLastModified);
                        fileList.add(hashMap);

                        System.out.println(hashMap);

                    }catch (Exception e){
                    }
                } else if(f.isDirectory()){
                    getLocalFiles(f.getAbsolutePath());
                }
            }
            return fileList;
        }
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();

        if (i == R.id.btn_choose_file) {
            showChoosingFile();
        } else if (i == R.id.btn_upload) {
            uploadFile();
        } else if (i == R.id.btn_download) {
            downloadFiles();
        } else if (i == R.id.btn_server_files){
            listServerFile();
        } else if (i == R.id.btn_local_file){
            listLocalFile();
        }
    }

    private void deleteLocalFile(){

        final String fileName = tvFileInfo.getText().toString();
        final String filePath = filepath + "/" + fileName;
        File file = new File(filePath);
        if (file.isFile() && file.exists()) {
            file.delete();
        }
    }

    private void showChoosingFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select File"), CHOOSING_FILE_REQUEST);
        Log.d("MainActivity", "Start Activity.");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        if (requestCode == CHOOSING_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            Log.d("MainActivity", "Get file uri: " + fileUri);
            if(fileUri != null){
                String fileName = getFileName(getApplicationContext(),fileUri);

                Log.d("MainActivity", "Get File name: " + fileName);
                if (fileName != null){
                    tvFileInfo.setText(fileName);
                    fileName = getFileNameNoEx(fileName);
                    edtFileName.setText(fileName);
                }else{
                    Toast.makeText(getApplicationContext(), "Choose file failed!", Toast.LENGTH_SHORT).show();
                }

//                    final String fileName = edtFileName.getText().toString();

                if (!validateInputFileName(edtFileName.getText().toString())) {
                    return;
                }
            }

        }

    }

    private String getFileExtension(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        return mime.getExtensionFromMimeType(contentResolver.getType(uri));
    }

    private String getFileExtension(String filename){
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot >-1) && (dot < (filename.length()))) {
                return filename.substring(dot + 1);
            }
        }
        return null;
    }

    private String getFileNameNoEx(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot >-1) && (dot < (filename.length()))) {
                return filename.substring(0, dot);
            }
        }
        return filename;
    }

    private boolean validateInputFileName(String fileName) {

        if (TextUtils.isEmpty(fileName)) {
            Toast.makeText(this, "Enter file name!", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void createFile(Context context, Uri srcUri, File dstFile) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
            if (inputStream == null) return;
            OutputStream outputStream = new FileOutputStream(dstFile);
            IOUtils.copy(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getFileName( final Context context, final Uri uri ) {
        final String scheme = uri.getScheme();
        String fileName = "";
        String data = null;
        if ( scheme == null )
            data = uri.getPath();

        // Pick from uri type is file
        else if ( ContentResolver.SCHEME_FILE.equals( scheme ) ) {
            data = uri.getPath();

         //Pick from uri type is content
        } else if ( ContentResolver.SCHEME_CONTENT.equals( scheme ) ) {

            if(DocumentsContract.isDocumentUri(context, uri)){
                String documentId = DocumentsContract.getDocumentId(uri);

                // Image pick from recent or images
                if ("com.android.providers.media.documents".equals(uri.getAuthority())) {

                    // Split at colon, use second item in the array
                    String id = DocumentsContract.getDocumentId(uri).split(":")[1];

                    // where id is equal to
                    String selection = MediaStore.Images.Media._ID + "=?";

                    String[] selectionArgs = {id};
                    data = getDataColumn(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs);

                    // Pick other document or image from downloads
                }else if("com.android.providers.downloads.documents".equals(uri.getAuthority())){
                    Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(documentId));
                    data = getDataColumn(context, contentUri, null, null);
                }else if("com.android.externalstorage.documents".equals(uri.getAuthority())){
                    fileName = documentId.substring(documentId.lastIndexOf("/") + 1, documentId.length());
                }else{
                    return null;
                }

            }

            else {
                // image pick from gallery or photo
                data = getDataColumn(context, uri, null, null);
            }

        }
        if(data!= null){
            fileName = data.substring(data.lastIndexOf("/") + 1, data.length());
        }

        return fileName;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        String path = null;

        String[] projection = new String[]{MediaStore.Images.Media.DATA};
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
                path = cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return path;
    }

    private boolean isPathExist(final String filepath){
        File file = new File(filepath);

        if (!file.exists())
        {
            file.mkdirs();
            if (file.mkdirs())
            {
                return true;
            }
            else
                return false;
        }
        return true;
    }

    public class getFileCallable implements Callable<List<HashMap<String, Object>>> {
        @Override
        public List<HashMap<String, Object>> call() throws Exception {
            List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String,Object>>();
            int seq = 0;

            ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                    .withBucketName(bucketName);
            ObjectListing objectListing;
            do {
                objectListing = s3Client.listObjects(listObjectsRequest);


                List<S3ObjectSummary> summaries = objectListing.getObjectSummaries();
                for (S3ObjectSummary objectSummary : summaries) {

                    ++seq;
                    HashMap<String, Object> hashMap = new HashMap<String, Object>();
                    hashMap.put("seq", seq);
                    hashMap.put("filename", objectSummary.getKey());
                    hashMap.put("ETag", objectSummary.getETag());
                    hashMap.put("size", objectSummary.getSize());
                    hashMap.put("LastModified", objectSummary.getLastModified());
                    fileList.add(hashMap);

                    System.out.println("filename:"+ objectSummary.getKey()+" fileLastModified:"+objectSummary.getLastModified());

                }
                listObjectsRequest.setMarker(objectListing.getNextMarker());
                return fileList;
            } while (objectListing.isTruncated());
        }
    }

    }

