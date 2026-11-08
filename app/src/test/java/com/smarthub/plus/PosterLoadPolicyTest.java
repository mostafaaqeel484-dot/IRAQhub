package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class PosterLoadPolicyTest {
 @Test public void cacheSurvivesDecoderFocus(){
  assertTrue(PosterLoadPolicy.cacheAllowed(true,"WWE"));
  assertFalse(PosterLoadPolicy.cacheAllowed(false,"WWE"));
  assertFalse(PosterLoadPolicy.cacheAllowed(true,"  "));
 }
 @Test public void networkNeverCompetesWith422(){
  assertFalse(PosterLoadPolicy.networkAllowed(true,true,true,false,"Match"));
  assertFalse(PosterLoadPolicy.networkAllowed(true,false,true,false,"Match"));
  assertFalse(PosterLoadPolicy.networkAllowed(true,false,false,true,"Match"));
  assertTrue(PosterLoadPolicy.networkAllowed(true,false,false,false,"Match"));
  assertFalse(PosterLoadPolicy.networkAllowed(false,false,false,false,"Match"));
 }
}
