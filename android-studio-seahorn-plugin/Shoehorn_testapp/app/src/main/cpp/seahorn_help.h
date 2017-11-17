#ifndef	__SEAHORN_HELP_H
#define	__SEAHORN_HELP_H

#ifdef	__SEAHORN__
#define	seahorn_extern	extern "C"
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

#define seahorn_assume	__VERIFIER_assume
#define seahorn_assert	assert

#else
#define	seahorn_extern
#define seahorn_assume(a)
#define seahorn_assert(a)
#endif

/*
 * To do:
 *  - 
 *  - Add a checklist function to list verified functions with a hash
 *
 */



#endif	/* !__SEAHORN_HELP_H */
