#include <jni.h>
#include <string>

#include "seahorn_help.h"

/* Intended to be some other random functions in the library */
int
dummy_function01()
{
    return 0;
}
int
dummy_function02()
{
    return 0;
}


seahorn_extern
int
pass_example()
{
	int n, k, j;
    int z;
    z = 10;
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
    int z;
    z = 10;
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
