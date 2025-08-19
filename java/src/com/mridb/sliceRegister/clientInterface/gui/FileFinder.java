
package com.mridb.sliceRegister.clientInterface.gui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import com.mridb.sliceRegister.Util;
import com.mridb.sliceRegister.mri.Nifti;

public class FileFinder {

	String subjectName;
	String subjectFolder;
	String rootDataFolder = "";
	String inputFilePath = "";
	String mriFolder = "";
	String eegFolder = "";
	String subjectId = "";
	boolean useMni = false;
	boolean hiRes = false;
	boolean loRes = false;
	String functionalSubfolder = "";


	public FileFinder() {

	}
	
	public static String[] getSubjectFolders(String subjectsFolder) {
		File subFile = new File(subjectsFolder);
		String[] subs = subFile.list();
		ArrayList<String> result = new ArrayList<String>();
		for(int i = 0; i < subs.length; i++) {
			if(subs[i].toLowerCase().startsWith("sub-")) {
				result.add(subs[i]);
			}
		}
		Collections.sort(result);
		return result.toArray(new String[result.size()]);
	}

	public void setInputFilePath(String value) {
		this.inputFilePath = value;
		File inputFile = new File(value);
		boolean exists = inputFile.exists();
		if(exists) {
			this.subjectId = "";
			// Z:\data\analyzed\sub-710-01\mri\functional\sub-710-01_ses-01_run-01_RSFC_901_mcf_avg.nii.gz
			// Z:\data\analyzed\sub-710-01\mri\functional\sliceVolumeV2\sub-710-01_ses-01_run-01_RSFC_901\\sub-710-01_ses-01_run-01_RSFC_901_mcf_avg.nii.gz
			// Z:\data\analyzed\sub-710-01\mri\functional\sliceVolumeV2\sub-710-01_ses-01_run-01_RSFC_901\\sub-710-01_ses-01_run-01_RSFC_901_mcf_avg.nii.gz
			// Z:\data\analyzed\sub-710-01\mri\anatomical\T1.nii.gz
			String[] parts = fileparts(value);
			for(int i = 0; i < parts.length; i++) {
				String part = parts[i].toLowerCase();
				if(part.startsWith("sub-")) {
					if(this.subjectId.isEmpty()) {
						this.subjectId = parts[i];
						StringBuilder rootBuilder = new StringBuilder();
						for(int j = 0; j < i; j++) {
							rootBuilder.append(parts[j]);
							rootBuilder.append(File.separator);
						}
						this.rootDataFolder = rootBuilder.toString();
						this.mriFolder = this.rootDataFolder + this.subjectId + File.separator; 
						if(new File(this.mriFolder + "mri").exists()) {
							this.mriFolder = this.mriFolder + "mri" + File.separator;
						}
						if(new File(this.mriFolder + "eeg").exists()) {
							this.eegFolder = this.mriFolder + "eeg" + File.separator;
						}
						int dummy = 1;
					}
				}
			}
		}
	}
	
	public String[] getRawFmriFiles() {
		File mriFolder = new File(this.mriFolder);
		String[] folders = mriFolder.list();
		ArrayList<String> result = new ArrayList<String>();
		for(int i = 0; i < folders.length; i++) {
			if(folders[i].toLowerCase().startsWith("ses-")) {
				//				try {
				String sessionPath = Util.fullfile(this.mriFolder, folders[i], "func");
				File sessionFolder = new File(sessionPath);
				File[] files = sessionFolder.listFiles();
				if(files != null) {

					for(int j = 0; j < files.length; j++) {
						String file = files[j].getName();
						if(Nifti.isNiftiPath(file)) {
							result.add(Util.fullfile(sessionPath, file));
						}
					}
				}
				//				} catch (IOException e) {
				//					// TODO Auto-generated catch block
				//					e.printStackTrace();
				//				}
			}
		}
		return result.toArray(new String[result.size()]);
	}
	public String getMriFolder() {
		return this.mriFolder;
	}
	public String[] getFunctionalVersions() {
		String mriFolderPath = null;
		mriFolderPath = Util.fullfile(this.mriFolder, "functional");
		File mriFolder = new File(mriFolderPath);
		if(!mriFolder.exists()) {
			mriFolderPath = Util.fullfile(this.mriFolder, "func");
			mriFolder = new File(mriFolderPath);
		}
		String[] files = mriFolder.list();

		ArrayList<String> result = new ArrayList<String>();
		result.add("standard");
		String[] excludedDirs = new String[] {"bandPass", "mean"};
		for(int i = 0; i < files.length; i++) {
			File file = null;
			file = new File(Util.fullfile(mriFolderPath, files[i])); 
			if(file.isDirectory()) {
				if(!files[i].toLowerCase().endsWith("_mcf.mat")) {
					boolean isExcluded = false;
					for(int j = 0; j < excludedDirs.length; j++) {
						if(files[i].contentEquals(excludedDirs[j])) {
							isExcluded = true;
						}
					}
					if(!isExcluded) {
//						try { result.add(Util.fullfile(mriFolderPath, files[i]));
//						} catch (IOException e) { e.printStackTrace(); }
						result.add(files[i]);
						
					}
				}
			}
		}
		return result.toArray(new String[result.size()]);
	}
	public void setFunctionalVersion(String version) throws FileNotFoundException {
		File versionFile = new File(version);
		if(versionFile.exists()) {
			this.functionalSubfolder = version;			
		} else {
			String versionPath = getFuctionalVersionPath(version);
			versionFile = new File(versionPath);
			if(versionFile.exists()) {
				this.functionalSubfolder = versionPath;
			} else {
				throw new FileNotFoundException("functional version file does not exist: " + versionPath);
			}
		}
	}
	public String getFuctionalVersionPath(String folder) {
		String mriFolderPath = null;
		mriFolderPath = Util.fullfile(this.mriFolder, "functional");
		if(!new File(mriFolderPath).exists()) {
			mriFolderPath = Util.fullfile(this.mriFolder, "func");
		}
		if(folder.contentEquals("standard")) {
			return mriFolderPath;
		} else {
			return Util.fullfile(mriFolderPath, folder); 
		}
	}
	
	public String[] getMotionCorrectedFmriFiles() {

//		String mriFolderPath = Util.fullfile(this.mriFolder, "functional", functionalSubfolder);
		String mriFolderPath = functionalSubfolder;
		File mriFolder = new File(mriFolderPath);
		String[] files = mriFolder.list();
		
		String toMatch = "_mcf_unwarp.nii";
		if(this.useMni) {
			toMatch = "_mcf_unwarp_MNI.nii";
		}
		
		ArrayList<String> result = new ArrayList<String>();
		if(files != null) {
			boolean finished = false;
			while(!finished) {
				for(int i = 0; i < files.length; i++) {
					if(files[i].toLowerCase().contains(toMatch)) {
						result.add(files[i]);
					}
				}
				if(result.isEmpty()) {
					if(toMatch.contentEquals("_mcf_unwarp.nii") || toMatch.contentEquals("_mcf_unwarp_MNI.nii")) {
						toMatch = ".nii.gz";
					} else {
						finished = true;
					}
				} else {
					finished = true;
				}
			}
		}

		return result.toArray(new String[result.size()]);
	}
	
	private static String[] fileparts(String value) {
		boolean useBackslash = true;
		String[] frontslashParts = Util.splitStringIgnoreEmpty(value, "/");
		String[] backslashParts = Util.splitStringIgnoreEmpty(value, "\\");
		String[] parts = backslashParts;
		if(backslashParts.length < frontslashParts.length) {
			useBackslash = false;
			parts = frontslashParts;
		}
		return parts;
	}
	
	public String getT1Nii() throws IOException {
		if(this.useMni) {
			return Util.fullfile(this.mriFolder, "anatomical", "T1.nii.gz");
		} else {
			return Util.fullfile(this.mriFolder, "anatomical", "T1_MNI.nii.gz");
		}
	}
	
	public String getT2Nii() throws IOException {
		if(this.useMni) {
			return Util.fullfile(this.mriFolder, "anatomical", "T2.nii.gz");
		} else {
			return Util.fullfile(this.mriFolder, "anatomical", "T2_MNI.nii.gz");
		}
	}
	public String getT2SpaceOfT1Nii() throws IOException {
		if(this.useMni) {
			return Util.fullfile(this.mriFolder, "anatomical", "T2_T1space.nii.gz");
		} else {
			return Util.fullfile(this.mriFolder, "anatomical", "T2_MNI.nii.gz");
		}
	}

	private String cleanupHemisphereInput(String hemisphere) {
		hemisphere = hemisphere.toUpperCase();
		if(!hemisphere.contentEquals("L") && !hemisphere.contentEquals("R")) {
			if(hemisphere == null || hemisphere.isEmpty()) {
				hemisphere = "L";
			} else if(hemisphere.charAt(0) == 'R') {
				hemisphere = "R";
			} else {
				hemisphere = "L";
			}
		}
		return hemisphere;
	}
	public String[] getLabelFilenames() throws IOException {
		String[] result = new String[] {
				getFSLRFilename("aparc.a2009s", "L", ".label.gii"),
				getFSLRFilename("aparc.a2009s", "R", ".label.gii"),
		};
		return result;
	}

	private String getGiftiFilename(String surface, String hemisphere) throws IOException {
		return getFSLRFilename(surface, hemisphere, ".surf.gii");
	}

	private String getFSLRFilename(String surface, String hemisphere, String extension) throws IOException {
//		hemisphere = hemisphere.toUpperCase();
//		if(!hemisphere.contentEquals("L") && !hemisphere.contentEquals("R")) {
//			if(hemisphere == null || hemisphere.isEmpty()) {
//				hemisphere = "L";
//			} else if(hemisphere.charAt(0) == 'R') {
//				hemisphere = "R";
//			} else {
//				hemisphere = "L";
//			}
//		}
		hemisphere = cleanupHemisphereInput(hemisphere);
		String volumeFolder = "NativeVol";
		if(this.useMni) {
			volumeFolder = "MNI";
		}
		String resFolder = "Native";
		String suffix = ".native" + extension;
		if(this.hiRes) {
			resFolder = "";
			suffix = ".164k_fs_LR" + extension;
		} else if(this.loRes) {
			resFolder = "fsaverage_LR32k";
			suffix = ".32k_fs_LR" + extension;
		}
		
		
		String filename = this.subjectId + "." + hemisphere + "." + surface + suffix;
		String result = Util.fullfile(this.mriFolder, "fs_LR", volumeFolder, resFolder, filename);
		return result;


		// file paths for surface giftis (from lesson.fmri.loadMidthicknessSurface)
		//	    
		// 				 {'MNI\^^SUBJECT^^.L.^^SURFACE^^.164k_fs_LR.surf.gii'                         }...
		//	             {'MNI\^^SUBJECT^^.R.^^SURFACE^^.164k_fs_LR.surf.gii'                         }...
		//	             {'MNI\Native\^^SUBJECT^^.L.^^SURFACE^^.native.surf.gii'                      }...
		//	             {'MNI\Native\^^SUBJECT^^.R.^^SURFACE^^.native.surf.gii'                      }...
		//	             {'MNI\fsaverage_LR32k\^^SUBJECT^^.L.^^SURFACE^^.32k_fs_LR.surf.gii'          }...
		//	             {'MNI\fsaverage_LR32k\^^SUBJECT^^.R.^^SURFACE^^.32k_fs_LR.surf.gii'          }...
		//	             {'NativeVol\^^SUBJECT^^.L.^^SURFACE^^.164k_fs_LR.surf.gii'                   }...
		//	             {'NativeVol\^^SUBJECT^^.R.^^SURFACE^^.164k_fs_LR.surf.gii'                   }...
		//	             {'NativeVol\Native\^^SUBJECT^^.L.^^SURFACE^^.native.surf.gii'                }...
		//	             {'NativeVol\Native\^^SUBJECT^^.R.^^SURFACE^^.native.surf.gii'                }...
		//	             {'NativeVol\fsaverage_LR32k\^^SUBJECT^^.L.^^SURFACE^^.32k_fs_LR.surf.gii'    }...
		//	             {'NativeVol\fsaverage_LR32k\^^SUBJECT^^.R.^^SURFACE^^.32k_fs_LR.surf.gii'    }...

		//		  {^^SUBJECT^^.L.^^SURFACE^^.164k_fs_LR.surf.gii'                         }...
		//        {^^SUBJECT^^.L.^^SURFACE^^.164k_fs_LR.surf.gii'                   }...
		//        {^^SUBJECT^^.R.^^SURFACE^^.164k_fs_LR.surf.gii'                         }...
		//        {^^SUBJECT^^.R.^^SURFACE^^.164k_fs_LR.surf.gii'                   }...
		//        {^^SUBJECT^^.L.^^SURFACE^^.native.surf.gii'                      }...
		//        {^^SUBJECT^^.L.^^SURFACE^^.native.surf.gii'                }...
		//        {^^SUBJECT^^.R.^^SURFACE^^.native.surf.gii'                      }...
		//        {^^SUBJECT^^.R.^^SURFACE^^.native.surf.gii'                }...
		//        {^^SUBJECT^^.L.^^SURFACE^^.32k_fs_LR.surf.gii'          }...
		//        {^^SUBJECT^^.L.^^SURFACE^^.32k_fs_LR.surf.gii'    }...
		//        {^^SUBJECT^^.R.^^SURFACE^^.32k_fs_LR.surf.gii'          }...
		//        {^^SUBJECT^^.R.^^SURFACE^^.32k_fs_LR.surf.gii'    }...
	}
	
	public String[] getPialGiftis() throws IOException {
		return new String[] {
				getGiftiFilename("pial", "L"),
				getGiftiFilename("pial", "R"),
		};
	}
	public String[] getMidthicknessGiftis() throws IOException {
		return new String[] {
				getGiftiFilename("midthickness", "L"),
				getGiftiFilename("midthickness", "R"),
		};
	}
	public String[] getWhiteGiftis() throws IOException {
		return new String[] {
				getGiftiFilename("white", "L"),
				getGiftiFilename("white", "R"),
		};
	}
	

	public void setUseMni() {
		this.useMni = true;
	}
	public void setUseNative() {
		this.useMni = false;
	}
	public void setHighResolution() {
		hiRes = true;
		loRes = false;
	}
	public void setMidResolution() {
		hiRes = false;
		loRes = false;
	}
	public void setLowResolution() {
		hiRes = false;
		loRes = true;
	}
	public static void testFslrFolders() throws IOException {
		
		String path = "Z:\\data\\analyzed\\sub-710-01\\mri\\fs_LR\\NativeVol\\Native\\sub-710-01.L.sulc.native.shape.gii";

//		String path = "Z:\\data\\analyzed\\sub-710-01\\mri\\functional\\sub-710-01_ses-01_run-01_RSFC_901_mcf_avg.nii.gz";
		FileFinder finder = new FileFinder();
		finder.setInputFilePath(path);
		
		ArrayList<String> files = new ArrayList<String>();
		ArrayList<Boolean> exist = new ArrayList<Boolean>();
		
		finder.setUseMni();
		String[] giis = finder.getPialGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getPialGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setHighResolution();
		
		finder.setUseMni();
		giis = finder.getPialGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getPialGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setLowResolution();
		
		finder.setUseMni();
		giis = finder.getPialGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getPialGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());
		
		
		
		finder.setUseMni();
		giis = finder.getMidthicknessGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getMidthicknessGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setHighResolution();
		
		finder.setUseMni();
		giis = finder.getMidthicknessGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getMidthicknessGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setLowResolution();
		
		finder.setUseMni();
		giis = finder.getMidthicknessGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getMidthicknessGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());
		
		

		finder.setUseMni();
		giis = finder.getWhiteGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getWhiteGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setHighResolution();
		
		finder.setUseMni();
		giis = finder.getWhiteGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getWhiteGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setLowResolution();
		
		finder.setUseMni();
		giis = finder.getWhiteGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());

		finder.setUseNative();
		giis = finder.getWhiteGiftis();
		files.add(giis[0]);
		exist.add(new File(giis[0]).exists());
		files.add(giis[1]);
		exist.add(new File(giis[1]).exists());
		
		for(int i = 0; i < files.size(); i++) {
			String a = files.get(i);
			Boolean b = exist.get(i);
			System.out.println(b.toString() + " " + a);
		}
		int dummy = 1;
		
	}
	public String getSequenceName() {
		String mriFolder = this.getMriFolder();
		if(this.inputFilePath.startsWith(mriFolder)) {
			String remainder = inputFilePath.substring(mriFolder.length()); 
			int dummy = 1;
		}
		return "";
	}
	public String getRsfcInfomapCiftiPath() {
		String mriFolder = this.getMriFolder();
		String result = Util.fullfile(mriFolder, "infomap", "RSFC", "rawassn_minsize10_regularized_allcolumns_recolored.dscalar.nii");
		return result;
	}
	public static void main(String[] args) throws IOException {
		FileFinder.testFslrFolders();
		FileFinder finder =new FileFinder();
		finder.setInputFilePath("Z:\\data\\analyzed\\sub-710-01\\mri\\functional");
		finder.getSequenceName();
		finder.getFunctionalVersions();
	}


}
