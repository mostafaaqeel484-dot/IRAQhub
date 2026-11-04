package com.smarthub.plus
class ProviderResolver(private val db:ChannelAliasDatabase){fun resolve(channel:String):ChannelAliasDatabase.Entry?=resolve(db.channel(channel))
 fun resolve(entry:ChannelAliasDatabase.Entry?):ChannelAliasDatabase.Entry?=entry?.provider?.takeIf{it.isNotBlank()}?.let{db.provider(it)}}
