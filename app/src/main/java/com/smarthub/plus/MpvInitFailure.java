package com.smarthub.plus;

/**
 * Keep the actual root cause of ClassNotFound/NoClassDefFoundError/native
 * dlopen errors; a one-word error type hid the useful information on X5M.
 */
final class MpvInitFailure {
 private MpvInitFailure(){}
 static String describe(Throwable failure){
  if(failure==null)return "UNKNOWN";
  StringBuilder result=new StringBuilder();
  Throwable current=failure;
  int depth=0;
  java.util.IdentityHashMap<Throwable,Boolean> seen=new java.util.IdentityHashMap<>();
  while(current!=null&&depth<5&&!seen.containsKey(current)){
   seen.put(current,Boolean.TRUE);
   if(depth>0)result.append(" <- ");
   result.append(current.getClass().getSimpleName());
   String message=current.getMessage();
   if(message!=null&&!message.trim().isEmpty())
    result.append(": ").append(safe(message));
   if(result.length()>560)break;
   current=current.getCause();
   depth++;
  }
  return result.length()>640?result.substring(0,640):result.toString();
 }
 private static String safe(String message){
  String line=message.replace('\n',' ').replace('\r',' ').replace('\t',' ');
  return line.length()>240?line.substring(0,240):line;
 }
}