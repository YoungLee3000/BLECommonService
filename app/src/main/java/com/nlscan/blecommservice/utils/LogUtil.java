package com.nlscan.blecommservice.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtil {
    public static void saveLog(String msg){
        if (msg == null)return;
        File file = new File("/sdcard/Newland/log/BadgeDisconnectedLog.txt");
        try {
            if (!new File("/sdcard/Newland/log/").exists())new File("/sdcard/Newland/log/").mkdir();
            if (!file.exists()){
                file.createNewFile();
            } else {
                if (getFileSize(file)/1024 > 100){//100kb
                    //file.delete();
                    File bakfile = new File("/sdcard/Newland/log/BadgeDisconnectedLog-bak.txt");
                    if (bakfile.exists())bakfile.delete();
                    else bakfile.createNewFile();
                    file.renameTo(bakfile);
                    file = new File("/sdcard/Newland/log/BadgeDisconnectedLog.txt");
                    file.createNewFile();
                }
            }
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileWriter writer = new FileWriter(file, true);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
            Date date = new Date(System.currentTimeMillis());
            String time = simpleDateFormat.format(date);
            String content = time+" "+msg+"\n";
            writer.write(content);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * 获取指定文件大小
     * @param
     * @return
     * @throws Exception
     */
    private static long getFileSize(File file){
        long size = 0;
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                size = fis.available();
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return size;
    }
}
