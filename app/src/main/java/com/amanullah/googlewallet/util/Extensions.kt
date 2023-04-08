package com.amanullah.googlewallet.util

import android.content.Context
import android.util.Log
import android.widget.Toast

fun String.showLog() {
    Log.e(this, this)
}

fun String.showToast(context: Context, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(context, this, duration).show()
}