package com.smarthub.plus
import org.json.JSONObject

/** Candidate IDs have already matched the requested name or its registered aliases. */
object DuplicateLogoChoices {
 private fun language(j:JSONObject):Int{val s=(j.optString("language",j.optString("languages"))+" "+j.optString("tags")).lowercase(java.util.Locale.ROOT);return if(s.contains("english")||Regex("\\ben\\b").containsMatchIn(s))2 else if(s.contains("arabic")||Regex("\\bar\\b").containsMatchIn(s))0 else 1}
 @JvmStatic fun choose(result:JSONObject,seed:String,name:String):List<String>{
  if(result.optString("status")!="matched")return emptyList()
  val candidates=result.optJSONArray("candidates")?:return emptyList();val ids=(0 until candidates.length()).map{candidates.getJSONObject(it).optString("id")}.toSet()
  val images=result.optJSONArray("images")?:return emptyList();val rows=(0 until minOf(200,images.length())).mapNotNull{images.optJSONObject(it)}.filter{it.optString("channel") in ids&&it.optString("url").startsWith("https://")};if(rows.isEmpty())return emptyList()
  val rank=rows.maxOf{language(it)};val best=rows.filter{language(it)==rank}.groupBy{it.optString("channel")}.toSortedMap().values.map{group->group.maxBy{it.optLong("width")*it.optLong("height")}.getString("url")}.distinct();val start=LogoSelection.index(seed,ChannelAliasDatabase.normalize(name),best.size);return best.drop(start)+best.take(start)
 }
}
