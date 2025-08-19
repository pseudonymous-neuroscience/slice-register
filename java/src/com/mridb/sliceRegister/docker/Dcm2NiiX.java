package com.mridb.sliceRegister.docker;

public class Dcm2NiiX {
	
	public static void pull() {
		String command = "docker pull pseudonymousneuroscience/dcm2niix";
		
	}
	
	public static void getDockerServiceLocation() {
		// windows
		String exePath = "\"C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe\"";
		
	}
	
	public static void writeDockerfile(String outputFolder) {
		String[] lines = new String[] {
				"FROM ubuntu:latest",
				"MAINTAINER <alexcohen@gmail.com> # feel free to change/adopt",
				"",
				"# Install Dependencies",
				"RUN apt-get update && apt-get upgrade -y && \\",
				"	apt-get install -y build-essential pkg-config cmake git pigz && \\",
				"	apt-get clean -y && apt-get autoclean -y && apt-get autoremove -y",
				"",
				"# Get dcm2niix from github and compile",
				"RUN cd /tmp && \\",
				"	git clone https://github.com/rordenlab/dcm2niix.git && \\",
				"	cd dcm2niix && mkdir build && cd build && \\",
				"	cmake -DBATCH_VERSION=ON -DUSE_OPENJPEG=ON .. && \\",
				"	make && make install",
				"",
				"# ENTRYPOINT [\"/usr/local/bin/dcm2niix\"]",
		};
	}

}
