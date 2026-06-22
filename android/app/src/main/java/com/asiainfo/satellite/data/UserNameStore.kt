package com.asiainfo.satellite.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 体验人姓名存储（仅本次会话有效）
 * 使用内存存储，不持久化到磁盘
 */
object UserNameStore {
    private var userName: String? = null
    
    /**
     * 设置体验人姓名
     */
    fun setUserName(name: String?) {
        userName = name?.ifBlank { null }
    }
    
    /**
     * 获取体验人姓名
     */
    fun getUserName(): String? {
        return userName
    }
    
    /**
     * 清除体验人姓名
     */
    fun clearUserName() {
        userName = null
    }
    
    /**
     * 检查是否已设置姓名
     */
    fun hasUserName(): Boolean {
        return !userName.isNullOrBlank()
    }
}