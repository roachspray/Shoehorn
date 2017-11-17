/*
 * Quickly slapped together class for parsing the android_gradle_build.json
 * JSON file. It just uses org.json package and is not all that smart.
 * There is a static parse() function that will return an object that 
 * represents only what is needed to do the SeaHorn bits...and not a full
 * parsed out version of the entire JSON. Makes lots of assumptions... fear.
 *
 */
package com.arrnaut.seahorn;

import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.json.JSONObject;
import org.json.JSONArray;

public class NativeBuildJSON {
	public static NativeBuildJSONParsed parse(String buildFile) throws java.io.IOException,
	  java.lang.OutOfMemoryError, java.lang.SecurityException,
	  java.nio.file.InvalidPathException {

		Path buildFilePath = FileSystems.getDefault().getPath(buildFile);
		return parse(buildFilePath);
	}
	public static NativeBuildJSONParsed parse(Path buildFilePath) throws java.io.IOException,
	  java.lang.OutOfMemoryError, java.lang.SecurityException,
	  java.nio.file.InvalidPathException {
		byte[] entireFileContents = Files.readAllBytes(buildFilePath);
		String strContents = new String(entireFileContents);	// XXX: No encoding specified

		JSONObject buildDesc = new JSONObject(strContents);		
		NativeBuildJSONParsed p = new NativeBuildJSONParsed();
		JSONArray cf;	
		try {
			cf = buildDesc.getJSONArray("cFileExtensions");
			for (int c = 0; c < cf.length(); c++) {
				String e = cf.getString(c);
				p.addCFileExtension(e);
			}
		} catch (Exception e) {
			cf = null;
		}
		JSONArray cppf;
		try {
			cppf = buildDesc.getJSONArray("cppFileExtensions");
			for (int c = 0; c < cppf.length(); c++) {
				String e = cppf.getString(c);
				p.addCPPFileExtension(e);
			}
		} catch (Exception e) {
			cppf = null;
		}
		if (cppf == null && cf == null) {
			// change this to something reasonable
			throw new java.io.IOException("No C/C++ extension definitions");
		}

		// These need to pass
		JSONObject lib = buildDesc.getJSONObject("libraries");
		java.util.Iterator<String> it = lib.keys();
		while (it.hasNext()) {
			String qIdx = (String)it.next();
			JSONObject arch = lib.getJSONObject(qIdx);
			String lt = arch.getString("artifactName");
			JSONArray files = arch.getJSONArray("files");
			for (int fl = 0; fl < files.length(); fl++) {
				JSONObject targ = (JSONObject)files.get(fl);
				String flags = targ.getString("flags");
				String src = targ.getString("src");
				p.addTargetFile(flags, src, lt);
			}	
		}
		return p;
	}
}
