# Android Studio Native Code and SeaHorn


It has been shown through surveys that one of the leading problems stifling the increase in use of formal methods in
(secure) software development is the lack of tools that are developer and development lifecycle
friendly (see the ACM survey and other articles in the references below).
This is increasingly a problem as the lifecycle for non-critical system software (e.g. your plain old app) has
increased in speed as CI/CD and DevOps increases. This is related to that in this walks the line of an
investigation into FM tools and usability...and re-usability. 

A month ago (?) @jared23 told me about XCode 9.1 having some improved support for handling the
[Design by Contract-like (DbC) constructs in Swift](https://swift.org/blog/xcode-9-1-improves-display-of-fatal-errors/).
These DbC functions are like precondition() and assert() and work at runtime. In theory, these also work
in non-runtime program analysis and in checking invariants. These things reminded Jared (correct me if I am wrong)
of the [SeaHorn](http://seahorn.github.io/) tool that we had encountered in recent years.  
 SeaHorn is an analysis tool
 that takes C/C++ source that is annotated with certain calls and produces Constrained [Horn Clauses](https://en.wikipedia.org/wiki/Horn_clause) (CHC) for
 verification..and subsequently checks those. It implements it's own abstract interpretation "language"
 called CRAB to help with analysis of variable values and invariants (see the chapter on invariant generation
in Bradley and Manna's book listed in the references).
 It is a bit limited in the scope of usability, but is an interesting experiment into model checking.
In SeaHorn, one checks a single function and any inputs are considered non-deterministic inputs. The check
 is determined by the existence of seahorn_assert call(s) and possibly some seahorn_assume calls,
 which are only left in the code for the analysis and are removed for runtime. However, if you left
 the seahorn_assume and seahorn_assert functions in as actual runtime checks, then you would have what
 Swift has in their precondition/assert expectations checking. There are other verification tools maybe
 I could have used, but have been thinking about this one for awhile, so using it.

This wiki discusses integrating SeaHorn with Android Studio IDE so that one can do compile time verification
 of any native C/C++ code. This is in the spirit of some work Galois Inc did on proofs of correctness of
 TLS handshake and  corking state machine in Amazon's s2n – a tls/ssl implementation. They not only did
 the proofs (combination of Coq and model checking with SAW), but they incorporated it into the build process,
 which is pretty sweet (see [pull req 565](https://github.com/awslabs/s2n/pull/565). I will share some
 ideas for how one can do this...a couple with code to back up and a couple just suggestions for other
 possibilities. 
Further, while there is the idea that one would have a requirement and design ahead of coding,
 there is also the idea that one would integrate such checks into existing code. This would be for a variety of
 reasons. Brandon mentioned that he thought perhaps static analysis could help in suggesting to the
 developer what code should have such compile-time DbC annotations. So I include some start to that here, as well. 
In general, there are a number of ways that one might use SeaHorn to check some of their code, for example:

1. Outside the IDE as a standalone tool
2. Extend Android Native Gradle plugin to offer SeaHorn use as a task
3. Create a Gradle plugin for Android Studio to do the same as the above
4. Create a python script that works as a 3rd party tool or execute_command()

Option (1) is totally doable, but want to keep inside IDE so goes at compile time.
Option (2) would be having to push code into a large codebase, which is unlikely; or, you
fork it, but that will have inability to keep up with current. Option (3) is not so bad, but
will be dealing with Java calling python which invokes a ELF executable.. so a bit of a 
chain that must be in the right setup. Option (4) is not so bad, either. 

Not discussed here is the fact that these methods currently would rely on the existence of a
special file created by the native build system called android_gradle_build.json that describes
the build commands. They will parse that and use some of it in arguments to SeaHorn.
 
There are many routes that could be taken here, but this is just a couple.  

## What you need

- Developed in Ubuntu 16.something
- Android Studio 3.0 (untested on 3.1)
- Gradle 4.3
- SeaHorn (off of master)
- Java 1.8
- Clang/LLVM 3.8 (for SeaHorn). I think I have one tool setup for 4.0, but will compile/work with 3.8
- [SingleOut](https://github.com/roachspray/SingleOut)
- ?? 

## Create a Gradle Plugin

This is all quite brief and to the point. I share the plugin, the configuration in Android Studio, and basic use.

Before getting started, you should build SingleOut and SeaHorn. For SeaHorn, you should apply the patch 
provided in the repo:

```
seahorn$ patch -p1 < seahorn_master.patch
patching file py/sea/commands.py
Hunk #1 succeeded at 767 (offset 7 lines).
Hunk #2 succeeded at 821 (offset 7 lines).
Hunk #3 succeeded at 835 (offset 7 lines).
Hunk #4 succeeded at 895 (offset 7 lines).
seahorn$
```

### Plugin Files

```
./Shoehorn/build.gradle
./Shoehorn/src/main/java/com/arrnaut/seahorn/NativeBuildJSON.java
./Shoehorn/src/main/java/com/arrnaut/seahorn/NativeBuildJSONParsed.java
./Shoehorn/src/main/java/com/arrnaut/seahorn/SeahornGradlePlugin.java
./Shoehorn/src/main/java/com/arrnaut/seahorn/SeahornTask.java
```

The SeahornGradlePlugin is the registration point for the plugin. The actual Gradle task is found in
SeaHornTask. It does much of the heavy lifting such as the execution of the SeaHorn. This plugin relies on the
existence of android_gradle_build.json file(s) existing. These specify much of the library specific build
 commands that are executed by the externalNativeBuild task of Android Studio. It provides us an easy way to
 access all the target files for a library and the command used to build them, including the paths for
 non-system/JNI includes. This is helpful for the part where the source is compiled out to LLVM IR as
 bitcode. So the NativeBuildJSON file has a static method parse() that will produce the NativeBuildJSONParsed
 object with this information. This is used by SeahornTask to check the configured files.


The main part of the plugin registration looks like:

```
public class SeahornGradlePlugin implements Plugin<Project> {
  static final String TASK_NAME = "seahornTask";
    @Override
    public void apply(Project t) {
        t.getTasks().create(TASK_NAME, SeahornTask.class);
    }
}
```


The SeaHornTask code, as mentioned does much of the SeaHorn execution

```
...
    NativeBuildJSONParsed parsed = NativeBuildJSON.parse((Path)jsonFiles[0]);
    List<NativeBuildJSONParsed.BuildTarget> targetFiles = parsed.getTargetFiles();
    java.util.Set<String> checkConfigKeys = checkConfig.keySet();
    for (String sourceFile : checkConfigKeys) {
      NativeBuildJSONParsed.BuildTarget sourceBT = null;
      for (NativeBuildJSONParsed.BuildTarget bt : targetFiles) {
...
      String incl = sourceBT.getIncludesString(); // In "...:...:.." form
      incl += ":" + studioIncludePath + ":" + studioIncludePath + "/linux"; // XXX
 
      List<String> fnsToVerify = checkConfig.get(sourceFile);
      for (String fn : fnsToVerify) {
 
        // Execute SeaHorn frontend with targeted function and the source file to generate bitcode
        Process p = Runtime.getRuntime().exec(new String[] {
          seahornPath, "fe",
          "--entry="+fn,
          "-I"+incl,
          "-O0",    // I had some bugs in the default -O3
          sourceBT.getFile(),
          "-o",
          taskDir + "/shoehorncheck.bc" // XXX
        });
...
        /*
         * Reduce target file to just main() and orig.main() fn definitions
         * Seems to fix a bug in SeaHorn, but could also there already
         * exists a command line flag for this. This is part of the SingleOut
         * project I made in GitHub.
         */
        p = Runtime.getRuntime().exec(new String[] {
          "/usr/lib/llvm-3.8/bin/opt",  // XXX TODO
          "-load",
          "/home/areiter/SingleOut/build/lib/libSingleOut.so",
          "-seahorn-body-rock",
          taskDir + "/shoehorncheck.bc",  // XXX
          "-o",
          taskDir + "/shoehorncheck-opt.bc"   // XXX
        });
...
        // Run the actual checking part
        p = Runtime.getRuntime().exec(new String[] {
          seahornPath,
          "pf",
          "-O0",
          taskDir + "/shoehorncheck-opt.bc"   // XXX
        });
        exitValue = p.waitFor();
        if (exitValue == 1) {
          System.out.println("seahorn: function "+fn+" failed in verification\n");
          throw new org.gradle.api.GradleException("seahorn: failed to verify function: "+fn);
        }
        System.out.println("seahorn: function "+fn+" passed\n");
...
```

Obviously a lot of hardcoded things... which need to be changed.
 
To build and distribute with maven, bump the version in build.gradle and

```
$ gradle assemble
$ gradle uploadArchives
$ ls ~/mavenrepo/arrnaut/Shoehorn/0.0.25
Shoehorn-0.0.25.jar  Shoehorn-0.0.25.jar.md5  Shoehorn-0.0.25.jar.sha1  Shoehorn-0.0.25.pom  Shoehorn-0.0.25.pom.md5  Shoehorn-0.0.25.pom.sha1
```

In the Git, it is configured for a local Maven repository, but however you do, do.

### Use in Android Studio

In the Project level build.gradle, add the local repo where you uploadArchives'd the plugin and the
dependency in green:

![alt text](https://github.com/roachspray/ShoeHornTEMP/blob/master/res/images/projectbuildgradle.png "project level build.gradle")

In the Module level build.gradle, where the native-lib code resides, will need the following added to the end:

![alt text](https://github.com/roachspray/ShoeHornTEMP/blob/master/res/images/modulebuildgradle.png "module level build.gradle")

Toward the bottom you will see the apply command to use the plugin. Then the specification of some
variables including location of the 'sea' binary from SeaHorn,
the Andriod Studio or other JRE includes path, and a hash map of files to what functions in those
 files should be checked with SeaHorn. So the 3 variables to be set are

- seahornPath
- studioIncludePath
- checkConfig

Below that, you will see a modification to the list of tasks. This is a hack, but it adds the
execution of the Seahorn plugin task upon exec of externalNativeBuildDebug task. This will hook it
 in to whenever there is a rebuild of the C/C++ code, thus re-checking. The one complaint is that it is
after build, so, doesn't really matter, but probably before? I am not sure. Things to think about though.

So in the above, we have configured that the native-lib.cpp file has two functions to be checked:
pass_example and fail_example. The idea being that they are both functions annotated with assume/assert
and can be checked; clearly one will pass and one will fail. The pass_example and fail_example code is
as follows, first showing seahorn_help.h:

```
#ifndef __SEAHORN_HELP_H
#define __SEAHORN_HELP_H
#ifdef  __SEAHORN__
#define seahorn_extern  extern "C"
extern "C" void __VERIFIER_assume(int);
extern "C" void __VERIFIER_error(void);
extern "C"
void
assert(int v)
{
        if (!v) {
                __VERIFIER_error();
        }
}
#define seahorn_assume  __VERIFIER_assume
#define seahorn_assert  assert
#else
#define seahorn_extern
#define seahorn_assume(a)
#define seahorn_assert(a)
#endif
#endif // !__SEAHORN_HELP_H
```

```
#include <jni.h>
#include <string>
 
// Note the include
#include "seahorn_help.h"
 
/* Intended to be some other random functions in the library */
int dummy_function01() {    return 0; }
int dummy_function02() { return 0; }
 
// Note the seahorn_extern: optional as long as you know the mangled form of your function name when using C++
seahorn_extern
int
pass_example()
{
        int n, k, j;
        n = dummy_function01();
        k = dummy_function02();
        seahorn_assume(n > 0);
        seahorn_assume(k > n);
        j = 0;
        while (j < n) {
                j++;
                k--;
        }
        seahorn_assert(k >= 0);
        return 0;
}
 
seahorn_extern
int
fail_example()
{
    int n, k, j;
    n = dummy_function01();
    k = dummy_function02();
    seahorn_assume(n > 0);
    seahorn_assume(k > 2);
    j = 0;
    while (j < n) {
        j++;
        k--;
    }
    seahorn_assert(k >= 0);
    return 0;
}
 
jstring
Java_com_example_shoehorn_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
```

Rebuilding in Android Studio we can see the execution of the plugin and the pass and failure results:

![alt text](https://github.com/roachspray/ShoeHornTEMP/blob/master/res/images/runtimeout.png "Running in Android Studio")

So, ok, cool. It is a bit hokey, but it works (smile) (big grin)

## Suggest to Add Check Annotations

Brandon had the idea of using some static analysis to aid in suggesting functions to add
annotations to; this would be for the case in which code is already written and you want to add
after the fact. The start of a pass to add multiple algorithms for determining this was started and is in the
[repo](https://github.com/roachspray/SingleOut/blob/master/PossiblyCheck.cpp) and the header file.

The current methods it has for trying to determine if a function should have checks added are:

- defaults to yes for any function
- defaults to no for any function
- cyclomatic complexity of function, which currently a hardcoded value for yes/no

Adding others is fairly easy to do and jsut involves modifications to the PossiblyCheck.cpp file.
Some ideas for methods to add for this might be:

- has lots of add/mult etc
- calls no other functions (i.e., less non-determinism to worry about??)
- type specific.. 
- other stuff ... this is something where could likely use some of Jared's gadget work

It is implemented as a LLVM pass that runs the available checks on each function, reporting back what
it determined (add annotations or no). To make things somewhat more wrapped up and to make use of the 
gradle build json file, I wrote a Python [wrapper script](https://github.com/roachspray/SingleOut/blob/master/PossiblyAnnotate).
This is also a decent example of how one could do the SeaHorn task via a external tool, but invoked from 
the build.

Adding an external tool to Android Studio is not so bad. You go to the preferences for Android Studio and
there is a category named "Tools"; under this there is a section for "External Tools". There you can 
add a tool and how it is to be executed.

![alt text](https://github.com/roachspray/ShoeHornTEMP/blob/master/res/images/setupexternal.png  "Setup an external tool")

 When you add it, it will be a part of the Tools->External Tools
menu bar, so it is easy to find and use.

![alt text](https://github.com/roachspray/ShoeHornTEMP/blob/master/res/images/runpossiblyannotate.png  "Run PossiblyAnnotate")

So, just need to improve the methods that determine if you should look into adding annotations or not. Whee, fun.


## References

```
Bradley, Manna, "The Calculus of Computation", Springer

Fisher, "Using Formal Methods to Eliminate Exploitable Bugs", USENIX Security, 2015

Fisher, et al., "The HACMS program: using formal methods to eliminate exploitable bugs", Philosophical
  Transactions of the Royal Society A, September 2017

Woodcock, et al., "Formal methods: Practice and experience", Journal of ACM Computer Surveys, 2009
```

## Acknowledgements

The author recognizes Jared Carlson, Brandon Creighton, and others on the Veracode Research team who
were willing to read an early draft and provide encouragement, as well as offer suggestions. Thank
you to Veracode for allowing the time for the author to write this and the code.

## Contact

You may contact the author at areiter@veracode.com or arr@watson.org (if need personal).

