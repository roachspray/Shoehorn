package com.arrnaut.seahorn;

import java.util.ArrayList;

public class NativeBuildJSONParsed {
	protected ArrayList<String> cFileExtensions;
	protected ArrayList<String> cppFileExtensions;

	class BuildTarget {
		protected String[] flags;
		protected String file;
		protected String libTarget;

		public BuildTarget(String flagStr, String file, String lib) {
			this.file = file;
			flags = flagStr.split(" "); // XXX
			libTarget = lib;
		} 
		public String getLibTarget() {
			return libTarget;
		}
		public String[] getFlags() {
			return flags;
		}
		public String getIncludesString() {
			String r = "."; // worry about this init
			boolean n = false;
			for (String s : flags) {
				if (s.equals("-isystem") || s.equals("-I")) {
					n = true;
					continue;
				}
				if (n) {
					r += ":" + s;
					n = false;
					continue;
				}
				if (s.startsWith("-I")) {
					r += ":" + s.substring(2);
				}
			}
			return r;
		}
		public String getFile() {
			return file;
		}
		public String getExtension() {
			String[] s = file.split(".");
			return s[s.length-1];
		}
	}
	protected java.util.ArrayList<BuildTarget> targetFiles;

	public NativeBuildJSONParsed() {
		cFileExtensions = new ArrayList<String>();
		cppFileExtensions = new ArrayList<String>();
		targetFiles = new java.util.ArrayList<BuildTarget>();
	}

	public void addCFileExtension(String e) {
		cFileExtensions.add(e);
	}
	public void addCPPFileExtension(String e) {
		cppFileExtensions.add(e);
	}

	public void addTargetFile(String flags, String file, String lt) {
		targetFiles.add(new BuildTarget(flags, file, lt));
	}

	public java.util.ArrayList<BuildTarget> getTargetFiles() {
		return targetFiles;
	}

	public String getFirstFile() {
		return ((BuildTarget)targetFiles.get(0)).getFile();
	}
} 
