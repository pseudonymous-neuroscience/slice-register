package com.mridb.sliceRegister.pipeline;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Properties;

import com.mridb.sliceRegister.Util;

public class Settings {

//	public String dataFolder = ".";
//	public ArrayList<String> dataSources;
	public String importedFolder = "/home/data/imported/";
	public String analyzedFolder = "/home/data/analyzed/";
	public Properties properties;
	
	public Settings() {
//		this.dataSources = new ArrayList<String>();
		this.properties = new Properties();
		
//		// default properties
//		this.properties.put("dataFolder",  ".");
	}

	private static Settings load() throws IOException {
		String json = Util.load(getConfigPath());
		Settings result = Util.newGson().fromJson(json, Settings.class);
		return result;
	}
	
	private Object getProperty(String key) {
		return this.properties.get(key);
	}
	private void setProperty(String key, Object value) {
		this.properties.put(key, value);
	}
	
	private void save() throws IOException {
		String json = Util.newGson().toJson(this);
		Util.save(getConfigPath(), json);
		
	}
	private static String getConfigPath() {
		// todo: implement user-specific pipeline settings
		return Util.getJarLocation() + "pipelineConfig.json";
	}


	public static void main(String[] args) {
		String path = Util.getJarLocation();
		Settings settings = new Settings();
		settings.setProperty("test", 1);
		try {
			settings.save();			
		} catch(IOException ex) {
			ex.printStackTrace();
		}
		Object field = settings.getProperty("dataFolder");
		
		
		int dummy = 1;

	}


}
