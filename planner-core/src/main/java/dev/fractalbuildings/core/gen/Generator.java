package dev.fractalbuildings.core.gen;

import dev.fractalbuildings.core.ir.SceneDoc;

public interface Generator {
	SceneDoc generate(GenRequest request);
}
