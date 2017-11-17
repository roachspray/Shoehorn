/*
 * This is the meat of the SeaHorn wrapper. I do not claim it is well-
 * written. Rather, it is ugly and I apologize over 1,000 times for that.
 *
 */
package com.arrnaut.seahorn;

import java.lang.Process;
import java.lang.Runtime;

import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;


public class SeahornTask extends DefaultTask {
	private String seahornPath;
	private String studioIncludePath;
	private Map<String, List<String>> checkConfig;

	public String getStudioIncludePath() {
		return studioIncludePath;
	}

	public void setStudioIncludePath(String studioIncludePath) {
		this.studioIncludePath = studioIncludePath;
	}

	public String getSeahornPath() {
		return seahornPath;
	}

	public void setSeahornPath(String seahornPath) {
		this.seahornPath = seahornPath;
	}

	public Map<String, List<String>> getCheckConfig() {
		return checkConfig;
	}

	public void setCheckConfig(Map<String, List<String>> checkConfig) {
		this.checkConfig = checkConfig;
	}

    @TaskAction
    public void seahornTask() throws Exception {
		/*
		 * At this point, we supposedly have been building
		 * native libraries. So, there should be some sort
		 * of JSON build remnant laying about...
		 *
		 */
		String taskDir = System.getProperty("user.dir");

		//System.out.println("seahornPath: "+ seahornPath);
		//System.out.println("seahornTask dir: "+ taskDir);

		Object[] jsonFiles = Files.find(Paths.get(taskDir), 1024,
		  (p, b) ->
		    b.isRegularFile() &&  \
		    p.getFileName().toString().endsWith("android_gradle_build.json")
		  ).toArray();
		assert(jsonFiles.length > 0);
		/*
		 * We just grab one as this plugin will be used at the app module level, which
		 * should mean that the libs will be built in the same path...so all libraries
		 * are listed in the JSON for that module. The concern is if build flavors
		 * impact files...
		 * I think one partial solution is to union all files listed in each JSON. To do.
		 *
		 */
		NativeBuildJSONParsed parsed = NativeBuildJSON.parse((Path)jsonFiles[0]);
		List<NativeBuildJSONParsed.BuildTarget> targetFiles = parsed.getTargetFiles();
		java.util.Set<String> checkConfigKeys = checkConfig.keySet();
		for (String sourceFile : checkConfigKeys) {
			NativeBuildJSONParsed.BuildTarget sourceBT = null;
			for (NativeBuildJSONParsed.BuildTarget bt : targetFiles) {
				String targetFileAbsPath = bt.getFile();
				if (targetFileAbsPath.endsWith(sourceFile)) {
					sourceBT = bt;
					break;
				}
			}
			if (sourceBT == null) {
				System.out.println("shoehorn: unable to find build information for target file: "+sourceFile);
				continue;
			}	
			String incl = sourceBT.getIncludesString();	// In "...:...:.." form
			incl += ":" + studioIncludePath + ":" + studioIncludePath + "/linux"; // XXX
		
			List<String> fnsToVerify = checkConfig.get(sourceFile);
			for (String fn : fnsToVerify) {
				// Execute SeaHorn frontend with targeted function and the source file
				Process p = Runtime.getRuntime().exec(new String[] { 
				  seahornPath, "fe",
				  "--entry="+fn,
				  "-I"+incl,
				  "-O0",		// I had some bugs in the default -O3
				  sourceBT.getFile(),
				  "-o",
				  taskDir + "/shoehorncheck.bc"	// XXX... need to fix paths :-/
				});	
				/*
				 * Being able to rely on the exit value implies that our SeaHorn setup has the
				 * patch included in this repo applied. I have not tried to PR it to the project
				 * but might be worth doing...just think there is more work than I really want 
				 * to do on that side to make the PR solid.
				 *
				 */
				int exitValue = p.waitFor();
				if (exitValue != 0) {
					System.out.println("seahorn: function "+fn+" failed in SingleOut opt\n");
					throw new org.gradle.api.GradleException("seahorn: failed to verify function: "+fn);
				}

				/*
				 * Reduce target file to just main() and orig.main() fn definitions
				 * Seems to fix a bug in SeaHorn, but could also there already
				 * exists a command line flag for this. I should probably go and
				 * verify if that is true or not, but so it goes for now....
				 *
				 */
				p = Runtime.getRuntime().exec(new String[] {
				  "/usr/lib/llvm-3.8/bin/opt",	// XXX TODO
				  "-load",
				  "/home/areiter/SingleOut/build/lib/libSingleOut.so",
				  "-seahorn-body-rock",
				  taskDir + "/shoehorncheck.bc",	// XXX
				  "-o",
				  taskDir + "/shoehorncheck-opt.bc"		// XXX
				});
				exitValue = p.waitFor();
				if (exitValue != 0) {
					System.out.println("seahorn: function "+fn+" failed in SingleOut opt\n");
					throw new org.gradle.api.GradleException("seahorn: failed to verify function: "+fn);
				}

				/*
				 * XXX.....
				 */
				p = Runtime.getRuntime().exec(new String[] {
				  seahornPath,
				  "pf",
				  "-O0",
				  taskDir + "/shoehorncheck-opt.bc"
				});
				// Exit value comment (above) applies here, as well.
				exitValue = p.waitFor();
				if (exitValue == 1) {
					System.out.println("seahorn: function "+fn+" failed in verification\n");
					throw new org.gradle.api.GradleException("seahorn: failed to verify function: "+fn);
				}
				System.out.println("seahorn: function "+fn+" passed\n");
			}
			System.out.println("seahorn: checked all functions configured for "+sourceBT.getFile());
		}
		return;
    }
}
