package br.com.stant.libraries.cardshowviewtakenpicturesview;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.databinding.BindingAdapter;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import br.com.stant.libraries.cardshowviewtakenpicturesview.databinding.CardShowTakenPicturePreviewDialogBinding;
import br.com.stant.libraries.cardshowviewtakenpicturesview.databinding.CardShowTakenPictureViewBinding;
import br.com.stant.libraries.cardshowviewtakenpicturesview.domain.enums.CardShowTakenPictureStateEnum;
import br.com.stant.libraries.cardshowviewtakenpicturesview.domain.model.CardShowTakenImage;
import br.com.stant.libraries.cardshowviewtakenpicturesview.utils.AppPermissions;
import br.com.stant.libraries.cardshowviewtakenpicturesview.utils.FileUtil;


/**
 * Created by denisvieira on 07/06/17.
 */
//
//@InverseBindingMethods(value = {
//        @InverseBindingMethod(type = CardShowTakenPictureView.class,
//                attribute = "bind:updatedAt",
//                method = "getFilterValue",
//                event = "android:filterStringValuetAttrChanged"),
//        @InverseBindingMethod(type = CardShowTakenPictureView.class,
//                attribute = "bind:updatedAt",
//                method = "getFilterValue",
//                event = "android:filterStringValuetAttrChanged"),
//})

public class CardShowTakenPictureView extends LinearLayout implements CardShowTakenPictureViewContract {

    private static final String TEMP_IMAGE_BASE_NAME = "card_show_taken_picture_temp_image";

    private static final int REQUEST_CHOOSER_IMAGE = 1;

    private CardShowTakenPictureViewBinding mCardShowTakenPictureViewBinding;
    private CardShowTakenPicturePreviewDialogBinding mCardShowTakenPicturePreviewDialogBinding;
    private Context mContext;
    private Activity mActivity;
    private CardShowTakenPictureViewImagesAdapter mCardShowTakenPictureViewImagesAdapter;
    private Fragment mFragment;
    private Dialog mPreviewPicDialog;

    private File mPhotoTaken;

    File sdcardTempImagesDir = FileUtil.getFile();
    public boolean canEditState;
    private CardShowTakenPictureViewContract.OnSavedCardListener mOnSavedCardListener;

    public CardShowTakenPictureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;

        mCardShowTakenPictureViewBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.card_show_taken_picture_view, this, true);
        mCardShowTakenPictureViewBinding.setHandler(this);
        mCardShowTakenPictureViewBinding.setCardStateEnum(CardShowTakenPictureStateEnum.NORMAL);
        unblockEditStateViewConfiguration();

        setOrientation(HORIZONTAL);

        mCardShowTakenPictureViewImagesAdapter = new CardShowTakenPictureViewImagesAdapter(getContext(), new ArrayList<CardShowTakenImage>(0), this);
        setExampleImages();


        RecyclerView.LayoutManager layout = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);

        mCardShowTakenPictureViewBinding.cardShowTakenPictureImageListRecyclerView.setLayoutManager(layout);
        mCardShowTakenPictureViewBinding.cardShowTakenPictureImageListRecyclerView.setNestedScrollingEnabled(true);
        mCardShowTakenPictureViewBinding.cardShowTakenPictureImageListRecyclerView.setFocusable(false);
        mCardShowTakenPictureViewBinding.cardShowTakenPictureImageListRecyclerView.setAdapter(mCardShowTakenPictureViewImagesAdapter);

        checkIfHasImages();

        FileUtil.createTempDirectory(sdcardTempImagesDir);

        mPreviewPicDialog = new Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        mCardShowTakenPicturePreviewDialogBinding = DataBindingUtil.inflate(LayoutInflater.from(mContext), R.layout.card_show_taken_picture_preview_dialog, null, false);
        mCardShowTakenPicturePreviewDialogBinding.setHandler(this);
        mPreviewPicDialog.setContentView(mCardShowTakenPicturePreviewDialogBinding.getRoot());

    }

    @BindingAdapter(value = {"pictureByName", "updatedAt"}, requireAll = false)
    public static void setBinding(CardShowTakenPictureView view,
                                  String mPictureByName, Date updatedAt) {

        if(mPictureByName != null)
            view.mCardShowTakenPictureViewBinding.setPictureByName(mPictureByName);

        if(updatedAt != null){
            String pattern = "MM/dd/yyyy";
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            view.mCardShowTakenPictureViewBinding.setUpdatedAt(format.format(updatedAt));
        }
    }

    @Override
    public void checkIfHasImages(){
        if(mCardShowTakenPictureViewImagesAdapter.getItemCount() == 0)
            showEditStateViewConfiguration(this);
        else
            cancelEditImagesStateViewConfiguration(this);
    }

    @Override
    public void showPreviewPicDialog(CardShowTakenImage cardShowTakenImage) {
        mCardShowTakenPicturePreviewDialogBinding.setImageUrl(cardShowTakenImage.getImagePath());
        mPreviewPicDialog.show();
    }

    @Override
    public void closePreviewPicDialog(View View) {
        mPreviewPicDialog.cancel();
    }

    @Override
    public void showEditStateViewConfiguration(View view) {
        mCardShowTakenPictureViewImagesAdapter.saveOriginalList();
        mCardShowTakenPictureViewBinding.setCardStateEnum(CardShowTakenPictureStateEnum.EDIT);
        blockEditStateViewConfiguration();
        mCardShowTakenPictureViewImagesAdapter.notifyDataSetChanged();
    }

    @Override
    public void saveImageStateViewConfiguration(View view) {
        mCardShowTakenPictureViewBinding.setCardStateEnum(CardShowTakenPictureStateEnum.NORMAL);
        unblockEditStateViewConfiguration();
        mCardShowTakenPictureViewImagesAdapter.saveEditData();

        List<CardShowTakenImage> imagesAsAdded = mCardShowTakenPictureViewImagesAdapter.getImagesAsAdded();
        List<CardShowTakenImage> imagesAsRemoved = mCardShowTakenPictureViewImagesAdapter.getImagesAsRemoved();

//        FileUtil.saveImage();

        mOnSavedCardListener.onSaved(imagesAsAdded, imagesAsRemoved);
    }

//    private List<CardShowTakenImage> saveImagesAsAdded(List<CardShowTakenImage> cardShowTakenImages){
//
//
//    }

    private List<String> convertImagesIntoBase64(List<CardShowTakenImage> cardShowTakenImages){
        List<String> base64Images = new ArrayList<>();
        for (CardShowTakenImage cardShowTakenImage : cardShowTakenImages) {

            base64Images.add(FileUtil.convertBitmapToBase64(cardShowTakenImage.getImageBitmap()));
        }

        return base64Images;
    }

    @Override
    public void cancelEditImagesStateViewConfiguration(View view) {
        mCardShowTakenPictureViewBinding.setCardStateEnum(CardShowTakenPictureStateEnum.NORMAL);
        unblockEditStateViewConfiguration();
        mCardShowTakenPictureViewImagesAdapter.cancelEditData();
        mOnSavedCardListener.onCancel();
    }

    @Override
    public void blockEditStateViewConfiguration() {
        canEditState = false;
        mCardShowTakenPictureViewBinding.setCanEditState(false);
    }

    @Override
    public void unblockEditStateViewConfiguration() {
        canEditState = true;
        mCardShowTakenPictureViewBinding.setCanEditState(true);
    }

    @Override
    public void setCardStateEnum(CardShowTakenPictureStateEnum cardStateEnum) {
        mCardShowTakenPictureViewBinding.setCardStateEnum(cardStateEnum);
    }

    @Override
    public void setOnSavedCardListener(CardShowTakenPictureViewContract.OnSavedCardListener onSavedCardListener) {
        mOnSavedCardListener = onSavedCardListener;
    }

    public void setFragment(Fragment fragment){
        mFragment = fragment;
        mActivity = fragment.getActivity();
    }

    public void setActivity(Activity activity){
        mActivity = activity;
    }

    public CardShowTakenPictureStateEnum getActualCardState(){
       return mCardShowTakenPictureViewBinding.getCardStateEnum();
    }

    public void setCardImages(List<CardShowTakenImage> cardShowTakenImages){
        mCardShowTakenPictureViewImagesAdapter.replaceData(cardShowTakenImages);
    }

    public List<CardShowTakenImage> getCardImages(){
        return mCardShowTakenPictureViewImagesAdapter.getData();
    }

    private void setExampleImages(){
        List<CardShowTakenImage> images = new ArrayList<>();
        images.add(new CardShowTakenImage("http://www.cityofsydney.nsw.gov.au/__data/assets/image/0009/105948/Noise__construction.jpg"));
        images.add(new CardShowTakenImage("http://facility-egy.com/wp-content/uploads/2016/07/Safety-is-important-to-the-construction-site.png"));

        setCardImages(images);
    }

    @Override
    public void pickPictureToFinishServiceInspectionFormFilled(View view) {
        if (!AppPermissions.hasPermissions((mActivity))) {
            AppPermissions.requestPermissions(mActivity);
        } else {
            dispatchTakePictureOrPickGalleryIntent();
        }
    }

    @Override
    public void dispatchTakePictureOrPickGalleryIntent() {
        Intent galleryPickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Intent takePhotoIntent   = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        String pickTitle = getResources().getString(R.string.card_show_taken_picture_view_request_chooser_msg);
        Intent chooserIntent = Intent.createChooser(galleryPickIntent, pickTitle);
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { takePhotoIntent });

        if (chooserIntent.resolveActivity(getContext().getPackageManager()) != null) {
            mPhotoTaken = FileUtil.prepareFile(takePhotoIntent);
            if(mFragment != null)
                mFragment.startActivityForResult(chooserIntent, REQUEST_CHOOSER_IMAGE);
            else if(mActivity != null)
                mActivity.startActivityForResult(chooserIntent, REQUEST_CHOOSER_IMAGE);
        }
    }

    public void addImageOnActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CHOOSER_IMAGE && resultCode == Activity.RESULT_OK && data.getData() == null) {

            CardShowTakenImage cardShowTakenImage = generateCardShowTakenImageFromCamera(mPhotoTaken, mActivity);
            mCardShowTakenPictureViewImagesAdapter.addPicture(cardShowTakenImage);
            mCardShowTakenPictureViewBinding.cardShowTakenPictureImageListRecyclerView.smoothScrollToPosition(mCardShowTakenPictureViewImagesAdapter.getItemCount()-1);

        }else if(requestCode == REQUEST_CHOOSER_IMAGE && resultCode == Activity.RESULT_OK && data.getData() != null) {

            CardShowTakenImage cardShowTakenImage = generateCardShowTakenImageFromImageGallery(data, mActivity);
            mCardShowTakenPictureViewImagesAdapter.addPicture(cardShowTakenImage);
            mCardShowTakenPictureViewBinding.cardShowTakenPictureImageListRecyclerView.smoothScrollToPosition(mCardShowTakenPictureViewImagesAdapter.getItemCount()-1);

        }else{
            mPhotoTaken = null;
        }

    }

    private Bitmap fixImageOrientation(Bitmap bitmap, String imageUrl){
                ExifInterface exif = null;
        try {
            exif = new ExifInterface(imageUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("6")) {
            bitmap = FileUtil.rotateBitmap(bitmap, 90);
        } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("8")) {
            bitmap = FileUtil.rotateBitmap(bitmap, 270);
        } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("3")) {
            bitmap = FileUtil.rotateBitmap(bitmap, 180);
        }

        return bitmap;
    }

    private CardShowTakenImage generateCardShowTakenImageFromCamera(File photoTaken, Activity activity){
        Bitmap bitmapImageFromIntentPath = FileUtil.createBitFromPath(photoTaken.getAbsolutePath());
        String tempImagePath = createTempImageFile(bitmapImageFromIntentPath, activity);

        return new CardShowTakenImage(bitmapImageFromIntentPath, tempImagePath);
    }

    private CardShowTakenImage generateCardShowTakenImageFromImageGallery(Intent data, Activity activity){

        Uri selectedImageUri = data.getData();

        String[] projection = {MediaStore.Images.Media.DATA};
        String res = "";
        Cursor cursor = getContext().getContentResolver().query(selectedImageUri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
            res = cursor.getString(column_index);
            cursor.close();
        }

        String realPathOfPhotoTaken = res;
        Bitmap bitmapImageFromIntentPath = FileUtil.createBitFromPath(realPathOfPhotoTaken);
        String tempImagePath = createTempImageFile(bitmapImageFromIntentPath, activity);

        return new CardShowTakenImage(bitmapImageFromIntentPath, tempImagePath);
    }

    private String createTempImageFile(Bitmap bitmap, Activity activity){
        String indexTempImage = mCardShowTakenPictureViewImagesAdapter.getItemCount()+1+"";

        return MediaStore.Images.Media.insertImage(activity.getContentResolver(),
                bitmap, TEMP_IMAGE_BASE_NAME+indexTempImage, null);
    }


    public boolean hasUpdatedAt(){
        return mCardShowTakenPictureViewBinding.getUpdatedAt() != null;
    }

    public boolean hasPictureByName(){
        return mCardShowTakenPictureViewBinding.getPictureByName() != null;
    }



}
