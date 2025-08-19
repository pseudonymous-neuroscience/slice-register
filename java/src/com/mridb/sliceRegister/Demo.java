package com.mridb.sliceRegister;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.mridb.sliceRegister.HttpWrapper;
import com.mridb.sliceRegister.Util;
import com.mridb.sliceRegister.clientInterface.gui.FileFinder;
import com.mridb.sliceRegister.mri.Nifti;
import com.mridb.sliceRegister.mri.SliceRegistrationPerformer;

public class Demo {
	
	public static void runDemo() throws IOException {
		System.out.println("Executing demonstration");
////		String url = "https://github.com/datalad/example-dicom-functional";
//		String url = "https://github.com/datalad/example-dicom-functional/tree/master/dicoms";
//		HttpWrapper httpWrapper = new HttpWrapper();
		String userDir = System.getProperty("user.dir");
		String dataDir = Util.fullfile(userDir, "data", "nii");
		String outputDir = Util.fullfile(userDir, "data", "registrations");
		String niiGzFilename = "dicoms_func_task-oneback_run-1_20140425155335_401.nii.gz";
		String niiGzPath = Util.fullfile(dataDir, niiGzFilename);	
		if(!(new File(niiGzPath).exists())) {
			String niiFilename = "dicoms_func_task-oneback_run-1_20140425155335_401.nii";
			String niiPath = Util.fullfile(dataDir, niiFilename);
			Nifti nii = Nifti.loadFromFile(niiPath);
			nii.save(niiGzPath);
		}
		
		SliceRegistrationPerformer.processFile(niiGzPath, outputDir);
		
		int dummy = 1;
		
//		String page = httpWrapper.webRead(url, "");
//
//		// "path":"dicoms/MR.1.3.46.670589.11.38317.5.0.4476.2014042516042547586","contentType":"file"
//		String linkStart = "\"path\":\"";
////		String linkEnd = "\",\"contentType\":\"file\"";
//		String linkEnd = "\"";
//		
//		List<String> links = new ArrayList<String>();
//		String remaining = page;
//		boolean finished = false;
//		while(!finished) {
//			int startIndex = remaining.indexOf(linkStart);
//			if(startIndex > -1) {
//				remaining = remaining.substring(startIndex + linkStart.length());
//				int endIndex = remaining.indexOf(linkEnd);
//				String link = remaining.substring(0, endIndex);
//				links.add(link);				
//			} else {
//				finished = true;
//			}
//		}
//		
//		for(int i = 0; i < links.size(); i++) {
//			String link = links.get(i);
//			url = "https://github.com/datalad/example-dicom-functional/tree/master/" + link;
//			int slash = link.lastIndexOf('/') + 1;
//			String filename = link.substring(slash);
//			String filePath = Util.fullfile(dataDir, filename);
//			if(i == 0) {
//				Util.ensureFileHasFolder(filePath);		
//			}
//			if(new File(filePath).exists()) {
//				System.out.println(String.format("Skipping file %d of %d (already exists)", i + 1, links.size()));
//			} else {
//				int errorCount = 0;
//				boolean success = false;
//				while(errorCount < 5 && success == false) {
//					try {
//						System.out.println(String.format("Downloading file %d of %d", i + 1, links.size()));
//						httpWrapper.webSave(url, "", filePath);						
//						success = true;
//					} catch(java.io.IOException ex) {
//						errorCount++;
//						try {
//							Thread.sleep(500);
//						} catch (InterruptedException e) {
//							e.printStackTrace();
//						}
//					}
//				}
//				if(!success) {
//					throw new IOException("unable to download file");
//				}
//			}
//		}
//		
//		
//		int dummy = 1;
//		
//		String pagePath = Util.fullfile(dataDir, "page.html");
//		httpWrapper.webSave(url, "", pagePath);
	}
	
	public static void main(String[] args) throws IOException {
		runDemo();
		
	}

}
