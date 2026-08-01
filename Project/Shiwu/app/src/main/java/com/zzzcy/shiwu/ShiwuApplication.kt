package com.zzzcy.shiwu

import android.app.Application
import com.zzzcy.shiwu.data.ShiwuRepository

class ShiwuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShiwuRepository.init(this)
    }
}
