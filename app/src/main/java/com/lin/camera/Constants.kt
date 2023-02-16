package com.lin.camera

import android.Manifest

object Constants {

    const val TAG = "cameraX2";
    const val FILE_NAME_FORMAT = "yy-MM-dd-HH-mm-ss-SSS"
    const val REQUEST_CODE_PERMISSIONS = 200
    val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
}