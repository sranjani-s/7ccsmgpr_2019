package com.example.lenovo.filesync;

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.provider.DocumentsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.HashMap;
import java.util.List;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final String KEY = "KEY";
    private final String SECRET = "SECRET";
    private final String bucketName = "bucketName";

    private AmazonS3Client s3Client;
    private BasicAWSCredentials credentials;

    //track Choosing Image Intent
    private static final int CHOOSING_FILE_REQUEST = 1234;

    private TextView tvFileName;
    private ImageView imageView;
    private EditText edtFileName;
    private ListView lvItemList;
    private View specItemList;
    private TextView tvItemName;


    private Uri fileUri;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        imageView = findViewById(R.id.img_file);
        edtFileName = findViewById(R.id.edt_file_name);
        tvFileName = findViewById(R.id.tv_file_name);
        tvFileName.setText("");

        lvItemList = findViewById(R.id.lv_item_list);
        tvItemName = findViewById(R.id.tv_file_info);

        findViewById(R.id.btn_choose_file).setOnClickListener(this);
        findViewById(R.id.btn_upload).setOnClickListener(this);
        findViewById(R.id.btn_download).setOnClickListener(this);
        findViewById(R.id.btn_list_files).setOnClickListener(this);

        AWSMobileClient.getInstance().initialize(this, new AWSStartupHandler() {
            @Override
            public void onComplete(AWSStartupResult awsStartupResult) {
                Log.d("YourMainActivity", "AWSMobileClient is instantiated and you are connected to AWS!");
            }
        }).execute();

        credentials = new BasicAWSCredentials(KEY, SECRET);
        s3Client = new AmazonS3Client(credentials);


    }

    private void uploadFile() {
        //Log.d("YourMainActivity", "Get file uri: " + fileUri + " before upload.");

        if (fileUri != null) {

            final String fileName = edtFileName.getText().toString();
            Log.d("YourMainActivity", "Upload File: " + fileName);

            if (!validateInputFileName(fileName)) {
                return;
            }

            final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "/" + fileName);
            Log.d("YourMainActivity", "Get file path: " + file.getAbsolutePath());

            createFile(getApplicationContext(), fileUri, file);

            TransferUtility transferUtility =
                    TransferUtility.builder()
                            .context(getApplicationContext())
                            .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                            .s3Client(s3Client)
                            .build();

            TransferObserver uploadObserver =
                    transferUtility.upload(fileName + "." + getFileExtension(fileUri), file);

            uploadObserver.setTransferListener(new TransferListener() {

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        Toast.makeText(getApplicationContext(), "Upload Completed!", Toast.LENGTH_SHORT).show();

                        file.delete();
                    } else if (TransferState.FAILED == state) {
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

    private void listFile(){

        getFileCallable getFileCallable = new getFileCallable();
        FutureTask<List<HashMap<String, Object>>> result = new FutureTask<>(getFileCallable);
        new Thread(result).start();
        List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String,Object>>();
        try {
            fileList = result.get();

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

//        System.out.println(fileList);

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
                System.out.println(finalFileList.get(i));
            }
        });
    }

    /**
     * Taken from Stack Overflow - https://stackoverflow.com/a/26501296
     * Updates the ListView height based on its children
     *
     * @param listView the ListView to adjust
     */

    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            // pre-condition
            return;
        }

        int totalHeight = 0;
        int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.AT_MOST);
        for (int i = 0; i < listAdapter.getCount(); i++) {
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

                List<HashMap<String, Object>> fileList = new ArrayList<HashMap<String,Object>>();
                int seq = 0;

                ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                        .withBucketName(bucketName);
                ObjectListing objectListing;

                do {
                    objectListing = s3Client.listObjects(listObjectsRequest);

                    List<S3ObjectSummary> summaries = objectListing.getObjectSummaries();
                    for (S3ObjectSummary objectSummary : summaries) {

                        System.out.println( " - " + objectSummary.getKey() + "  " +
                                "(size = " + objectSummary.getSize() +
                                ")");

                        seq++;
                        HashMap<String, Object> hashMap = new HashMap<String, Object>();
                        hashMap.put("seq", seq);
                        hashMap.put("filename", objectSummary.getKey());
                        hashMap.put("ETag", objectSummary.getETag());
                        hashMap.put("size", objectSummary.getSize());
                        hashMap.put("LastModified", objectSummary.getLastModified());
                        fileList.add(hashMap);

                    }
                    listObjectsRequest.setMarker(objectListing.getNextMarker());
                    return fileList;
                } while (objectListing.isTruncated());

    }


    private void downloadFile() {

    }

    @Override
    public void onClick(View view) {
        int i = view.getId();

        if (i == R.id.btn_choose_file) {
            showChoosingFile();
        } else if (i == R.id.btn_upload) {
            uploadFile();
        } else if (i == R.id.btn_download) {
//            downloadFile();
        } else if (i == R.id.btn_list_files){
            listFile();
        }
    }

    private void showChoosingFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "Select File"), CHOOSING_FILE_REQUEST);
        Log.d("YourMainActivity", "Start Activity.");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (bitmap != null) {
            Log.d("YourMainActivity", "bitmap is not null.");
            bitmap.recycle();

        }

        if (requestCode == CHOOSING_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            Log.d("YourMainActivity", "Get file uri: " + fileUri);
            try {
                if(fileUri != null){
                    String path = getFilePath(getApplicationContext(),fileUri);
                    Log.d("YourMainActivity", "Get File path: " + path);
                    String b = path.substring(path.lastIndexOf("/") + 1, path.length());
                    Log.d("YourMainActivity", "Get File name: " + b);
                    b = getFileNameNoEx(b);
                    edtFileName.setText(b);
                    final String fileName = edtFileName.getText().toString();

                    if (!validateInputFileName(fileName)) {
                        return;
                    }
                }

                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), fileUri);
                Log.d("YourMainActivity", "Get file bitmap: " + bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private String getFileExtension(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        return mime.getExtensionFromMimeType(contentResolver.getType(uri));
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

    public static String getFilePath( final Context context, final Uri uri ) {
        final String scheme = uri.getScheme();
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
                }
            }

            else {
                // image pick from gallery or photo
                data = getDataColumn(context, uri, null, null);
            }

        }
        return data;
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

    public class getFileCallable implements Callable<List<HashMap<String, Object>>> {
        @Override
        public List<HashMap<String, Object>> call() throws Exception {
            return getFile();
        }
    }

}
