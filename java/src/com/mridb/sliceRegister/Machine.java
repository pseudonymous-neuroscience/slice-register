package com.mridb.sliceRegister;

import java.util.ArrayList;
import java.util.UUID;

import com.google.gson.Gson;

public class Machine {
	
	public static enum OperatingSystem{LINUX, WINDOWS, MAC, UNKNOWN}; 
	
	private UUID id;
	private UUID description;
	private UUID organization;
	private transient OperatingSystem osEnum;
	private transient OperatingSystem os;
	private String osName;
//	private String aesPass;
//	private AesKey aesKey;
	private String rsaPass;
	private EncryptedKeyPair keyPair;

	public static Machine CreateMachine() throws Exception{
		return Machine.CreateMachine(System.getProperty("os.name"));
	}

	public static Machine CreateMachine(String os) throws Exception{
		Machine machine = new Machine();		
		machine.id = UUID.randomUUID();
		machine.organization = null;
		machine.description = null;
		machine.osName = System.getProperty("os.name");
		machine.osEnum = getOperatingSystemEnum(os);
//		machine.aesPass = Util.randomString();
		machine.rsaPass = Util.randomString();
//		machine.aesKey = new AesKey(machine.aesPass);
//		machine.keyPair = EncryptedKeyPair.generateKeyPair(machine.aesPass, machine.aesKey.getSalt());
		machine.keyPair = EncryptedKeyPair.generateKeyPair(machine.rsaPass);
		return machine;
	}
	
	
	public UUID getDescription() {
		return description;
	}
	public UUID getOrganization() {
		return organization;
	}
//	public AesKey getAesKey() {
//		return aesKey;
//	}
	public EncryptedKeyPair getEncryptedKeyPair() {
		return keyPair;
	}
	public String getOperatingSystemName() {
		return this.osName;
	}
	public void setOperatingSystemName(String name) {
		this.osName = name;
		this.osEnum = getOperatingSystemEnum(name);
	}
	
	public void setDescription(UUID value) {
		description = value;
	}
	public void setOrganization(UUID value) {
		organization = value;
	}
	
	public static void main(String[] args) throws Exception {
		Machine m = Machine.CreateMachine();
		
		Gson gson = new Gson();
		String json = gson.toJson(m);
		Machine m1 = gson.fromJson(json, Machine.class);
		
		System.out.println(json);
		
		int dummy = 1;
		
	}
	
	
	
	private static OperatingSystem getOperatingSystemEnum(String osName) {
		if(isWindows(osName)) {
			return OperatingSystem.WINDOWS;
		}
		else if(isMac(osName)) {
			return OperatingSystem.MAC;
		}
		else if(isLinux(osName)) {
			return OperatingSystem.LINUX;
		}
		else {
			return OperatingSystem.UNKNOWN;
		}
	}
	
	


	public static boolean isWindows(String osName) {
		if(osName.startsWith("Windows")) {
			return true;
		}
		return false;
	}
	public static boolean isMac(String osName) {
		if(osName.startsWith("Mac")) {
			return true;
		}
		return false;
	}
	public static boolean isLinux(String osName) {
		if(osName.startsWith("Linux")) {
			return true;
		}
		return false;
	}
	
	public static boolean isWindows() {
		return isWindows(System.getProperty("os.name"));
	}
	public static boolean isMac() {
		return isMac(System.getProperty("os.name"));
	}
	public static boolean isLinux() {
		return isLinux(System.getProperty("os.name"));
	}


}
