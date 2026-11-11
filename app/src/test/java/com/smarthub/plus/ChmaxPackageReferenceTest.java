package com.smarthub.plus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

public final class ChmaxPackageReferenceTest {
 @SuppressWarnings("unchecked")
 private static List<String> resolve(String value)throws Exception{
  Method method=ChmaxLogoSyncWorker.class.getDeclaredMethod("candidates",String.class);
  method.setAccessible(true);
  try{return (List<String>)method.invoke(null,value);}
  catch(InvocationTargetException failure){
   Throwable cause=failure.getCause();
   if(cause instanceof Exception)throw (Exception)cause;
   throw failure;
  }
 }

 @Test public void canonicalisesAllObservedOfficialReferenceShapes()throws Exception{
  assertEquals("https://gchsicon.datasver.com/packages/2026.11/130/latest_png.zip",
    resolve("packages/2026.11/130/latest").get(0));
  assertEquals("https://gchsicon.datasver.com/icon/130/latest_png.zip",
    resolve("gchsicon.datasver.com/icon/130/latest").get(0));
  assertEquals("https://gchsicon.datasver.com/icon/130/latest_png.zip",
    resolve("http://gchsicon.datasver.com/icon/130/latest").get(0));
  assertEquals("https://gchsicon.datasver.com/icon/130/latest_png.zip",
    resolve("https://gchsicon.datasver.com/icon/130/latest_png.zip").get(0));
 }

 @Test public void rejectsCredentialsQueriesForeignHostsAndTraversal()throws Exception{
  for(String value:new String[]{"","https://evil.test/icon/x",
    "https://secret@gchsicon.datasver.com/icon/x",
    "https://gchsicon.datasver.com/icon/x?token=secret",
    "icon/../secret","icon//secret"}){
   try{resolve(value);fail("accepted unsafe CHMAX reference");}
   catch(Exception expected){/* rejected */}
  }
 }
}
