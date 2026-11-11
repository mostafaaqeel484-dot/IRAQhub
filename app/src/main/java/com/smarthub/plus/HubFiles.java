package com.smarthub.plus;
import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class HubFiles {
 public static File file(Context c,String name){if(!name.matches("[A-Za-z0-9_.-]+")||name.contains(".."))throw new IllegalArgumentException("Invalid filename");return new File(c.getFilesDir(),name);}
 public static JSONObject read(Context c,String name){try{File f=file(c,name);if(!f.isFile()||f.length()>12*1024*1024)return null;return new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}catch(Exception e){return null;}}
 public static void save(Context c,String name,JSONObject value)throws IOException{AtomicFile file=new AtomicFile(file(c,name));FileOutputStream out=null;try{out=file.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw new IOException("Could not save data",e);}}
 private HubFiles(){}
}
