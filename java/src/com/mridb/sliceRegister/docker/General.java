package com.mridb.sliceRegister.docker;

import com.mridb.sliceRegister.Executor;

public class General {
	
	public static class Image {
		
	}
	public static class Container {
		
	}
	
	public static String getContainer(String imageName) {
		return null;
	}
	
	public static String runContainer(String imageName) {
		return null;
	}
	
//	public static Container[] listContainers() {
//		
//	}
//	public static Image[] listImages() {
//		
//	}
	
	public static void copyToContainer() {
		
	}
	
	public static void copyFromContainer() {
		
	}
	
	public static void sendDockerCommand(String command) throws Exception {
		String toExecute = "docker " + command;
		String result = Executor.executeSyncronously(command);
	}
	
	
	
	public static void main(String[] args) {
		
	}

}
