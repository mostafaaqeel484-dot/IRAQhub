package com.smarthub.plus;
import java.time.LocalDate;import java.util.*;
interface SportsDataProvider {String name();List<SportsMatch> matches(LeagueRepository.League league,LocalDate day,String window)throws Exception;}
