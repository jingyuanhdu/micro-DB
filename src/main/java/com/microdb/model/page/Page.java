package com.microdb.model.page;

import com.microdb.transaction.TransactionID;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Page 抽象接口
 * 页，读写磁盘文件数据时以page为基本单位
 *
 * @author zhangjw
 * @version 1.0
 */
public interface Page {

    PageID getPageID();
    /**
     * 获得页面访问频率
     */
    AtomicLong getAccessFrequency();
    /**
     * 序列化page数据
     */
    byte[] serialize() throws IOException;

    /**
     * 反序列化pageData
     */
    void deserialize(byte[] pageData) throws IOException;

    // /**
    //  * 计算每页可存放的行数
    //  */
    // int calculateMaxSlotNum(TableDesc tableDesc);

    /**
     * 返回每页可存放的行数
     */
    int getMaxSlotNum();

    boolean isSlotUsed(int index);

    boolean hasEmptySlot();

    /**
     * 标记是否为脏页
     */
    void markDirty(TransactionID transactionID);

    boolean isDirty();

    TransactionID getDirtyTxId();

    /**
     * 保留页原始数据
     */
    void saveBeforePage();
    /**
     * 获取页在修改前的数据
     */
    Page getBeforePage();
}
