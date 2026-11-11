package com.smarthub.plus;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LauncherDataTest {
    private ReceiverChannelRepository.Entry entry(String name,String provider,String details,long id){return new ReceiverChannelRepository.Entry(name,"",provider,"test",details,id);}
    @Test public void importedMetadataNeverBecomesAPlayableChannel(){assertFalse(entry("MBC 2","MBC","11294 H · SID 2",-1).playable());assertTrue(entry("Published","","",42).playable());}
    @Test public void channelSearchIncludesFrequencyAndProvider(){List<ReceiverChannelRepository.Entry> rows=Arrays.asList(entry("Film HD","MBC","11294 H",-1),entry("Sport","Other","12345 V",-1));assertEquals("Film HD",ReceiverChannelRepository.search(rows,"11294",200).get(0).name);assertEquals(1,ReceiverChannelRepository.search(rows,"mbc",200).size());assertTrue(ReceiverChannelRepository.search(rows,"missing",200).isEmpty());}
    @Test public void channelSearchIsBounded(){List<ReceiverChannelRepository.Entry> rows=new ArrayList<>();for(int n=0;n<500;n++)rows.add(entry("Channel "+n,"","",-1));assertEquals(200,ReceiverChannelRepository.search(rows,"",200).size());}
    @Test public void missingSignalIsUnavailableNotZero(){ReceiverInfoRepository.Info info=new ReceiverInfoRepository.Info("","","","","","","","");assertEquals("SNR —  ·  BER —",info.signalDetail());assertFalse(info.signalDetail().contains("0"));}
    @Test public void actualZeroBerIsKept(){ReceiverInfoRepository.Info info=new ReceiverInfoRepository.Info("","","","","82%","87%","13.60 dB","0");assertEquals("SNR 13.60 dB  ·  BER 0",info.signalDetail());}
    @Test public void orbitalPositionsKeepWestAndEast(){assertEquals("13.0°E",ReceiverChannelRepository.orbital(130));assertEquals("7.0°W",ReceiverChannelRepository.orbital(3530));}
    @Test public void channelSearchHandlesSpacesArabicAlifAndArabicDigits(){List<ReceiverChannelRepository.Entry> rows=Arrays.asList(entry("MBC 2","MBC","11294 H",-1),entry("قناة أبوظبي","","",-1));assertEquals("MBC 2",ReceiverChannelRepository.search(rows,"MBC2",200).get(0).name);assertEquals("MBC 2",ReceiverChannelRepository.search(rows,"١١٢٩٤",200).get(0).name);assertEquals("قناة أبوظبي",ReceiverChannelRepository.search(rows,"ابوظبي",200).get(0).name);}
}
