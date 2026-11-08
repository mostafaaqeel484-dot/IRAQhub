package com.smarthub.plus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** No network: uncertain artwork may be shown, never a persistent identity. */
public final class SimilarPosterChoiceTest {
 private JSONObject film(String id,String title,int year)throws Exception{
  return new JSONObject().put("id",id).put("type","movie")
    .put("title",title).put("year",year)
    .put("poster","https://image.tmdb.org/t/p/w780/p.png");
 }
 @Test public void distinctiveSimilarTitleFindsTheNearestFilm()throws Exception{
  JSONObject nearest=film("tmdb:33","Harry Potter and the Prisoner of Azkaban",2004);
  JSONArray choices=new JSONArray().put(nearest)
    .put(film("tmdb:34","Potter Family Adventures",2004));
  assertSame(nearest,SimilarPosterChoice.choose(choices,"Harry Potter (2004)"));
 }
 @Test public void ambiguousEquallyGoodTitlesAreNotSilentlyAssigned()throws Exception{
  JSONArray choices=new JSONArray()
    .put(film("tmdb:33","Harry Potter and the Prisoner of Azkaban",2004))
    .put(film("tmdb:34","Harry Potter and the Chamber of Secrets",2004));
  assertNull(SimilarPosterChoice.choose(choices,"Harry Potter (2004)"));
 }
 @Test public void yearMismatchAndShortGenericTitleAreRejected()throws Exception{
  JSONArray choices=new JSONArray()
    .put(film("tmdb:33","Harry Potter and the Prisoner of Azkaban",2004));
  assertNull(SimilarPosterChoice.choose(choices,"Harry Potter (1994)"));
  assertNull(SimilarPosterChoice.choose(choices,"News"));
 }
 @Test public void wrongHostOrUnknownIdentityCannotBecomeAnApproximatePoster()throws Exception{
  JSONObject wrong=film("tmdb:33","Harry Potter and the Prisoner of Azkaban",2004);
  wrong.put("poster","https://example.org/poster.jpg");
  JSONArray choices=new JSONArray().put(wrong);
  assertNull(SimilarPosterChoice.choose(choices,"Harry Potter (2004)"));
  wrong.put("poster","https://image.tmdb.org/t/p/w780/p.png");
  wrong.put("id","not-tmdb");
  assertNull(SimilarPosterChoice.choose(choices,"Harry Potter (2004)"));
 }
}
