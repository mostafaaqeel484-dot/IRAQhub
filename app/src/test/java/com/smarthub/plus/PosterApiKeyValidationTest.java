package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class PosterApiKeyValidationTest {
 @Test public void sourceAPIKeysRejectUnsafeCharacters(){
  assertTrue(PosterApiSecrets.valid("test_key-123"));
  assertFalse(PosterApiSecrets.valid(""));
  assertFalse(PosterApiSecrets.valid("abc\\ndef"));
  assertFalse(PosterApiSecrets.valid("foo?bar"));
 }
 @Test public void privateTokensNeverContainKeys(){
  assertTrue(PrivatePosterCache.token("privateposter:rpdb:tt0133093"));
  assertTrue(PrivatePosterCache.token("privateposter:omdb:tt0133093"));
  assertFalse(PrivatePosterCache.token("https://api.ratingposterdb.com/secret/imdb/poster-default/tt0133093.jpg"));
 }
 @Test public void onlyKnownProvidersCanStoreKeys(){
  assertTrue(PosterApiSecrets.supported("opdb"));
  assertTrue(PosterApiSecrets.supported("rpdb"));
  assertTrue(PosterApiSecrets.supported("omdb"));
  assertTrue(PosterApiSecrets.supported("sportsdb"));
  assertFalse(PosterApiSecrets.supported("fake"));
 }
}