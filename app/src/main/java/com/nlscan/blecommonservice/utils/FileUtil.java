package com.nlscan.blecommonservice.utils;



import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class FileUtil {



    public static void writeString(String fileName, String str){
        FileOutputStream fos = null;
        OutputStreamWriter osw = null;
        BufferedWriter bw = null;

        try {
            fos = new FileOutputStream(new File(fileName),true);
            osw = new OutputStreamWriter(fos,"UTF-8");
            bw = new BufferedWriter(osw);

            bw.write(str +"\n");
        } catch (IOException e) {
            e.printStackTrace();

        }
        finally {
            closeStream(bw);
            closeStream(osw);
            closeStream(fos);
        }

    }



    public static void createDir(String path){
        File dir=new File(path);
        if(!dir.exists())
            dir.mkdir();
    }

    public static void createFile(String fileName){
        File file = new File(fileName);
        try {
            if (!file.exists())
                file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * close stream
     * @param closeable
     */
    private static void closeStream(Closeable closeable){
        if (closeable != null){
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
