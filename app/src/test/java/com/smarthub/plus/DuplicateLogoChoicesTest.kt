package com.smarthub.plus
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
class DuplicateLogoChoicesTest {
 @Test fun duplicatedPublicNamesSelectOnlyMatchedIdsAndRemainStable(){val j=JSONObject("""{"status":"matched","candidates":[{"id":"a"},{"id":"b"}],"images":[{"channel":"a","url":"https://example.org/a.png"},{"channel":"b","url":"https://example.org/b.png"},{"channel":"other","url":"https://example.org/wrong.png"}]}""");val choices=DuplicateLogoChoices.choose(j,"seed","Same channel");assertEquals(2,choices.size);assertFalse(choices.contains("https://example.org/wrong.png"));assertEquals(choices,DuplicateLogoChoices.choose(j,"seed","Same channel"))}
 @Test fun englishAndHighestQualityAreKeptWithinEachDuplicate(){val j=JSONObject("""{"status":"matched","candidates":[{"id":"a"}],"images":[{"channel":"a","language":"ar","width":4000,"height":4000,"url":"https://example.org/ar.png"},{"channel":"a","language":"en","width":100,"height":100,"url":"https://example.org/low.png"},{"channel":"a","language":"en","width":1000,"height":1000,"url":"https://example.org/high.png"}]}""");assertEquals(listOf("https://example.org/high.png"),DuplicateLogoChoices.choose(j,"seed","Same"))}
}
