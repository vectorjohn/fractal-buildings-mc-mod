package dev.fractalbuildings.core.gen.v1;

/** One pass of the shell pipeline for one building type: reads the context, writes its own phase's output. */
@FunctionalInterface
public interface Program {
	void run(BuildContext c);
}
