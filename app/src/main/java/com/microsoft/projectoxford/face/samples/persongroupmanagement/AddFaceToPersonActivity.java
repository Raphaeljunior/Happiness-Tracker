// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.projectoxford.face.samples.persongroupmanagement;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.samples.R;
import com.microsoft.projectoxford.face.samples.helper.ImageHelper;
import com.microsoft.projectoxford.face.samples.helper.LogHelper;
import com.microsoft.projectoxford.face.samples.helper.SampleApp;
import com.microsoft.projectoxford.face.samples.helper.StorageHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AddFaceToPersonActivity extends ActionBarActivity {
    // Background task of adding a face to person.
    class AddFaceTask extends AsyncTask<Void, String, Boolean> {
        List<Integer> mFaceIndices;
        AddFaceTask(List<Integer> faceIndices) {
            mFaceIndices = faceIndices;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // Get an instance of face service client to detect faces in image.
            FaceServiceClient faceServiceClient = SampleApp.getFaceServiceClient();
            try{
                publishProgress("Adding face...");
                UUID personId = UUID.fromString(mPersonId);

                for (Integer index: mFaceIndices) {
                    UUID faceId = mFaceGridViewAdapter.faceIdList.get(index);
                    addLog("Request: Adding face " + faceId.toString() + " to person " + mPersonId);
                    // Start the request to add face.
                    faceServiceClient.addPersonFace(mPersonGroupId, personId, faceId, "User Data");
                }
                return true;
            } catch (Exception e) {
                publishProgress(e.getMessage());
                addLog(e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPreExecute() {
            setUiBeforeBackgroundTask();
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            setUiDuringBackgroundTask(progress[0]);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            setUiAfterAddingFace(result, mFaceIndices);
        }
    }

    // Background task of face detection.
    private class DetectionTask extends AsyncTask<InputStream, String, Face[]> {
        private boolean mSucceed = true;

        @Override
        protected Face[] doInBackground(InputStream... params) {
            // Get an instance of face service client to detect faces in image.
            FaceServiceClient faceServiceClient = SampleApp.getFaceServiceClient();
            try{
                publishProgress("Detecting...");

                // Start detection.
                return faceServiceClient.detect(
                        params[0],   /* Input stream of image to detect */
                        false,       /* Whether to analyzes facial landmarks */
                        false,       /* Whether to analyzes age */
                        false,       /* Whether to analyzes gender */
                        false);      /* Whether to analyzes head pose */
            }  catch (Exception e) {
                mSucceed = false;
                publishProgress(e.getMessage());
                addLog(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            setUiBeforeBackgroundTask();
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            setUiDuringBackgroundTask(progress[0]);
        }

        @Override
        protected void onPostExecute(Face[] faces) {
            if (mSucceed) {
                addLog("Response: Success. Detected " + (faces == null ? 0 : faces.length)
                        + " Face(s)");
            }

            // Show the result on screen when detection is done.
            setUiAfterDetection(faces, mSucceed);
        }
    }

    private void setUiBeforeBackgroundTask() {
        mProgressDialog.show();
    }

    // Show the status of background detection task on screen.
    private void setUiDuringBackgroundTask(String progress) {
        mProgressDialog.setMessage(progress);
        setInfo(progress);
    }

    private void setUiAfterAddingFace(boolean succeed, List<Integer> faceIndices) {
        mProgressDialog.dismiss();
        if (succeed) {
            String faceIds = "";
            for (Integer index: faceIndices) {
                String faceId = mFaceGridViewAdapter.faceIdList.get(index).toString();
                faceIds += faceId + ", ";
                try {
                    File file = new File(getApplicationContext().getFilesDir(), faceId);
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    mFaceGridViewAdapter.faceThumbnails.get(index)
                            .compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                    fileOutputStream.flush();
                    fileOutputStream.close();

                    Uri uri = Uri.fromFile(file);
                    StorageHelper.setFaceUri(
                            faceId, uri.toString(), mPersonId, AddFaceToPersonActivity.this);
                } catch (IOException e) {
                    setInfo(e.getMessage());
                }
            }
            addLog("Response: Success. Face(s) " + faceIds + "added to person " + mPersonId);
            finish();
        }
    }

    // Show the result on screen when detection is done.
    private void setUiAfterDetection(Face[] result, boolean succeed) {
        mProgressDialog.dismiss();

        if (succeed) {
            // Set the information about the detection result.
            if (result != null) {
                setInfo(result.length + " face"
                        + (result.length > 1 ? "s" : "") + " detected");
            } else {
                setInfo("0 face detected");
            }

            // Set the adapter of the ListView which contains the details of the detected faces.
            mFaceGridViewAdapter = new FaceGridViewAdapter(result);

            // Show the detailed list of detected faces.
            GridView gridView = (GridView) findViewById(R.id.gridView_faces_to_select);
            gridView.setAdapter(mFaceGridViewAdapter);
        }
    }

    String mPersonGroupId;
    String mPersonId;
    String mImageUriStr;
    Bitmap mBitmap;
    FaceGridViewAdapter mFaceGridViewAdapter;

    // Progress dialog popped up when communicating with server.
    ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_face_to_person);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mPersonId = bundle.getString("PersonId");
            mPersonGroupId = bundle.getString("PersonGroupId");
            mImageUriStr = bundle.getString("ImageUriStr");
        }

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle(getString(R.string.progress_dialog_title));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString("PersonId", mPersonId);
        outState.putString("PersonGroupId", mPersonGroupId);
        outState.putString("ImageUriStr", mImageUriStr);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mPersonId = savedInstanceState.getString("PersonId");
        mPersonGroupId = savedInstanceState.getString("PersonGroupId");
        mImageUriStr = savedInstanceState.getString("ImageUriStr");
    }

    @Override
    protected void onResume() {
        super.onResume();

        Uri imageUri = Uri.parse(mImageUriStr);
        mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                imageUri, getContentResolver());
        if (mBitmap != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            InputStream imageInputStream = new ByteArrayInputStream(stream.toByteArray());
            addLog("Request: Detecting " + mImageUriStr);
            new DetectionTask().execute(imageInputStream);
        }
    }

    public void doneAndSave(View view) {
        if (mFaceGridViewAdapter != null) {
            List<Integer> faceIndices = new ArrayList<>();

            for (int i = 0; i < mFaceGridViewAdapter.faceIdList.size(); ++i) {
                if (mFaceGridViewAdapter.faceChecked.get(i)) {
                    faceIndices.add(i);
                }
            }

            if (faceIndices.size() > 0) {
                new AddFaceTask(faceIndices).execute();
            } else {
                finish();
            }
        }
    }

    // Add a log item.
    private void addLog(String log) {
        LogHelper.addIdentificationLog(log);
    }

    // Set the information panel on screen.
    private void setInfo(String info) {
        TextView textView = (TextView) findViewById(R.id.info);
        textView.setText(info);
    }

    private class FaceGridViewAdapter extends BaseAdapter {
        List<UUID> faceIdList;
        List<Bitmap> faceThumbnails;
        List<Boolean> faceChecked;

        FaceGridViewAdapter(Face[] detectionResult) {
            faceIdList = new ArrayList<>();
            faceThumbnails = new ArrayList<>();
            faceChecked = new ArrayList<>();

            if (detectionResult != null) {
                List<Face> faces = Arrays.asList(detectionResult);
                for (Face face : faces) {
                    try {
                        // Crop face thumbnail with five main landmarks drawn from original image.
                        faceThumbnails.add(ImageHelper.generateFaceThumbnail(
                                mBitmap, face.faceRectangle));

                        faceIdList.add(face.faceId);

                        faceChecked.add(false);
                    } catch (IOException e) {
                        // Show the exception when generating face thumbnail fails.
                        setInfo(e.getMessage());
                    }
                }
            }
        }

        @Override
        public int getCount() {
            return faceIdList.size();
        }

        @Override
        public Object getItem(int position) {
            return faceIdList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            // set the item view
            if (convertView == null) {
                LayoutInflater layoutInflater =
                        (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView =
                        layoutInflater.inflate(R.layout.item_face_with_checkbox, parent, false);
            }
            convertView.setId(position);

            ((ImageView)convertView.findViewById(R.id.image_face))
                    .setImageBitmap(faceThumbnails.get(position));

            // set the checked status of the item
            CheckBox checkBox = (CheckBox) convertView.findViewById(R.id.checkbox_face);
            checkBox.setChecked(faceChecked.get(position));
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    faceChecked.set(position, isChecked);
                }
            });

            return convertView;
        }
    }
}
