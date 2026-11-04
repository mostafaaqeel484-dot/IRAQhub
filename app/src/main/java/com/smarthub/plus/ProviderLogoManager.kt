package com.smarthub.plus
class ProviderLogoManager(private val db:ChannelAliasDatabase,private val images:ChannelLogoResolver){fun resolve(providerId:String)=db.provider(providerId)?.let{loads.incrementAndGet();images.image(it)};companion object{@JvmField val loads=java.util.concurrent.atomic.AtomicInteger()}}
