package com.mridb.sliceRegister.clientInterface.gui;

import java.time.Instant;

import com.mridb.sliceRegister.Util;

public class Time {
	
	private transient Instant instant;
	private long e;
	private int n;
	
	public Time() {
		this(Instant.now());
	}
	
	public Time(Instant ins) {
		setInstant(ins);
	}
	
	public void setInstant(Instant ins) {
		this.instant = ins;
		this.e = ins.getEpochSecond();
		this.n = ins.getNano();
	}
	public Instant getInstant() {
		Instant i = Instant.ofEpochSecond(n, e);
		this.instant = i;
		return i;
	}
	
	public String toString() {
		return Util.newGson().toJson(this);
	}
	
	public static void main(String[] args) {
		Time t = new Time();
		String s = t.toString();
		// gson cannot handle instants
		//		Instant i = t.instant;
		//		String si = Util.newGson().toJson(i);
		int dummy = 1;
	}

}
